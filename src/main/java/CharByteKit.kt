import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * Author: CharByteKit
 * Date: 2019/3/28
 */
object CharByteKit {
    @JvmStatic
    fun getBytes(chars: CharArray): ByteArray {
        val cs = StandardCharsets.UTF_8
        val cb = CharBuffer.allocate(chars.size)
        cb.put(chars)
        cb.flip()
        val bb = cs.encode(cb)
        return bb.array()
    }

    @JvmStatic
    fun getChars(bytes: ByteArray): CharArray {
        val cs = StandardCharsets.UTF_8
        val bb = ByteBuffer.allocate(bytes.size)
        bb.put(bytes).flip()
        val cb = cs.decode(bb)
        return cb.array()
    }

    @JvmStatic
    fun charToByte(c: Char): ByteArray {
        val b = ByteArray(2)
        b[0] = (c.code and 0xFF00 shr 8).toByte()
        b[1] = (c.code and 0xFF).toByte()
        return b
    }

    @JvmStatic
    fun byteToChar(b: ByteArray): Char {
        val hi = b[0].toInt() and 0xFF shl 8
        val lo = b[1].toInt() and 0xFF
        return (hi or lo).toChar()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // char[] <===> byte[]
        val c = getChars(byteArrayOf(65, 2, 3))
        println(c.contentToString())
        val b = getBytes(c)
        println(b.contentToString())

        // char <===> byte[]
        val b2 = charToByte('A')
        println(b2.contentToString())
        val c2 = byteToChar(b2)
        println(c2)
    }

    /**
     * 下载链接  下载到的目录  下载后的文件名称
     *
     * @param urlPath         urlPath
     * @param targetDirectory targetDirectory
     * @param fileName        fileName
     */
    fun download(
        urlPath: String,
        targetDirectory: String,
        fileName: String, onProgress: (Float, Long, Long) -> Unit = { _, _, _ -> }
    ): Boolean {
        runCatching {
            // 解决url中可能有中文情况
            val url = URL(urlPath)
            val http = url.openConnection() as HttpURLConnection
            http.connectTimeout = 60000
            http.setRequestProperty("Referer", urlPath)
            http.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)")

            // 获取文件大小
            val length = http.contentLengthLong
            var writeLength: Long = 0

            // 获取文件名
            val inputStream = http.inputStream
            val buff = ByteArray(1024 * 50)
            val file = File(targetDirectory, fileName)

            // 本地文件已经存在
            if (file.exists() && file.isFile && file.length() == length) {
                onProgress.invoke(1f, length, length)
                return true
            }

            // 删除
            if (file.isFile) {
                file.deleteOnExit()
            } else {
                file.deleteRecursively()
            }

            if (!file.exists() || file.length() == 0L) {
                inputStream.use { stream ->
                    val out: OutputStream = FileOutputStream(file)
                    var len: Int
                    while (stream.read(buff).also { len = it } != -1) {
                        out.write(buff, 0, len)
                        out.flush()
                        writeLength += len
                        val progress = writeLength / (length * 1f)
                        onProgress.invoke(progress, writeLength, length)
                    }
                    // 关闭资源
                    out.close()
                    stream.close()
                }
                http.disconnect()
                return true
            }
        }
        return false
    }
}