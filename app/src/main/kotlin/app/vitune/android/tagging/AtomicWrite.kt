package app.vitune.android.tagging

import android.util.AtomicFile

/** 寫到一半被中斷（當機、被系統砍）也不會留下壞掉的檔：成功才換上新檔，失敗保留舊檔。 */
internal fun writeAtomically(file: AtomicFile, text: String) {
    val out = file.startWrite()
    try {
        out.write(text.encodeToByteArray())
        file.finishWrite(out)
    } catch (e: Throwable) {
        file.failWrite(out)
        throw e
    }
}
