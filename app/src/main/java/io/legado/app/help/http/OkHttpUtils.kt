package io.legado.app.help.http

import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.GSON
import io.legado.app.utils.Utf8BomUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp客户端扩展函数，支持重试的HTTP响应获取
 * 
 * @param retry 重试次数，默认为0（不重试）
 * @param builder 请求构建器配置函数
 * @return HTTP响应对象
 * @throws Exception 当所有重试都失败时抛出最后一次的异常
 */
suspend fun OkHttpClient.newCallResponse(
    retry: Int = 0,
    builder: Request.Builder.() -> Unit
): Response {
    val requestBuilder = Request.Builder()
    requestBuilder.apply(builder)
    var response: Response? = null
    // 支持重试机制，失败时自动重试
    for (i in 0..retry) {
        response = newCall(requestBuilder.build()).await()
        if (response.isSuccessful) {
            return response
        }
    }
    return response!!
}

/**
 * 获取HTTP响应体，支持重试机制
 * 
 * @param retry 重试次数，默认为0
 * @param builder 请求构建器配置函数
 * @return 响应体对象
 * @throws IOException 当响应体为空或请求失败时
 */
suspend fun OkHttpClient.newCallResponseBody(
    retry: Int = 0,
    builder: Request.Builder.() -> Unit
): ResponseBody {
    return newCallResponse(retry, builder).let {
        it.body ?: throw IOException(it.message)
    }
}

/**
 * 获取字符串格式的HTTP响应，支持重试机制
 * 
 * @param retry 重试次数，默认为0
 * @param builder 请求构建器配置函数
 * @return StrResponse对象，包含响应和文本内容
 */
suspend fun OkHttpClient.newCallStrResponse(
    retry: Int = 0,
    builder: Request.Builder.() -> Unit
): StrResponse {
    return newCallResponse(retry, builder).let {
        StrResponse(it, it.body?.text() ?: it.message)
    }
}

/**
 * 将OkHttp的Call转换为协程的suspend函数
 * 支持协程取消机制，当协程被取消时会自动取消网络请求
 * 
 * @return HTTP响应对象
 * @throws IOException 网络请求失败时
 */
suspend fun Call.await(): Response = suspendCancellableCoroutine { block ->
    
    // 当协程被取消时，取消网络请求
    block.invokeOnCancellation {
        cancel()
    }

    // 异步执行网络请求
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            block.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            block.resume(response)
        }
    })

}

/**
 * 智能解析响应体为文本字符串
 * 按优先级顺序检测字符编码：指定编码 > HTTP头编码 > 内容自动检测
 * 
 * @param encode 指定的字符编码（可选）
 * @return 解析后的文本字符串
 */
fun ResponseBody.text(encode: String? = null): String {
    val responseBytes = Utf8BomUtils.removeUTF8BOM(bytes())
    var charsetName: String? = encode

    // 1. 优先使用指定编码
    charsetName?.let {
        return String(responseBytes, Charset.forName(charsetName))
    }

    // 2. 根据HTTP头判断编码
    contentType()?.charset()?.let { charset ->
        return String(responseBytes, charset)
    }

    // 3. 根据内容自动检测编码
    charsetName = EncodingDetect.getHtmlEncode(responseBytes)
    return String(responseBytes, Charset.forName(charsetName))
}

/**
 * 响应体解压缩处理
 * 检测Content-Type为application/zip时自动解压第一个文件
 * 
 * @return 解压后的响应体，如果不是zip格式则返回原响应体
 * @throws Exception 解压失败时抛出异常
 */
fun ResponseBody.decompressed(): ResponseBody {
    val contentType = contentType()?.toString()
    if (contentType != "application/zip") {
        return this
    }
    
    // 创建ZIP输入流并读取第一个条目
    val source = ZipInputStream(byteStream()).apply {
        try {
            nextEntry
        } catch (e: Exception) {
            close()
            throw e
        }
    }.source().buffer()
    return RealResponseBody(null, -1, source)
}

fun Request.Builder.addHeaders(headers: Map<String, String>) {
    headers.forEach {
        addHeader(it.key, it.value)
    }
}

fun Request.Builder.get(url: String, queryMap: Map<String, String>, encoded: Boolean = false) {
    val httpBuilder = url.toHttpUrl().newBuilder()
    queryMap.forEach {
        if (encoded) {
            httpBuilder.addEncodedQueryParameter(it.key, it.value)
        } else {
            httpBuilder.addQueryParameter(it.key, it.value)
        }
    }
    url(httpBuilder.build())
}

fun Request.Builder.get(url: String, encodedQuery: String?) {
    val httpBuilder = url.toHttpUrl().newBuilder()
    httpBuilder.encodedQuery(encodedQuery)
    url(httpBuilder.build())
}

private val formContentType = "application/x-www-form-urlencoded".toMediaType()

fun Request.Builder.postForm(encodedForm: String) {
    post(encodedForm.toRequestBody(formContentType))
}

fun Request.Builder.postForm(form: Map<String, String>, encoded: Boolean = false) {
    val formBody = FormBody.Builder()
    form.forEach {
        if (encoded) {
            formBody.addEncoded(it.key, it.value)
        } else {
            formBody.add(it.key, it.value)
        }
    }
    post(formBody.build())
}

fun Request.Builder.postMultipart(type: String?, form: Map<String, Any>) {
    val multipartBody = MultipartBody.Builder()
    type?.let {
        multipartBody.setType(type.toMediaType())
    }
    form.forEach {
        when (val value = it.value) {
            is Map<*, *> -> {
                val fileName = value["fileName"] as String
                val file = value["file"]
                val mediaType = (value["contentType"] as? String)?.toMediaType()
                val requestBody = when (file) {
                    is File -> {
                        file.asRequestBody(mediaType)
                    }

                    is ByteArray -> {
                        file.toRequestBody(mediaType)
                    }

                    is String -> {
                        file.toRequestBody(mediaType)
                    }

                    else -> {
                        GSON.toJson(file).toRequestBody(mediaType)
                    }
                }
                multipartBody.addFormDataPart(it.key, fileName, requestBody)
            }

            else -> multipartBody.addFormDataPart(it.key, it.value.toString())
        }
    }
    post(multipartBody.build())
}

fun Request.Builder.postJson(json: String?) {
    json?.let {
        val requestBody = json.toRequestBody("application/json; charset=UTF-8".toMediaType())
        post(requestBody)
    }
}