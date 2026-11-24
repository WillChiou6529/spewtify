package app.vitune.android.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 歌曲的曲風／心情標籤（Spewtify）。tag 是 providers.tags.Tag 的 id。
 * source：yt（官方歌單直接收錄）、yt_artist（依歌手推估）、lastfm、manual（使用者手動，自動分類不會覆蓋）。
 * weight ≤ 0 表示使用者手動移除這個標籤。
 */
@Immutable
@Entity(
    primaryKeys = ["songId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongTag(
    @ColumnInfo(index = true) val songId: String,
    @ColumnInfo(index = true) val tag: String,
    val source: String,
    val weight: Float
) {
    companion object {
        const val SOURCE_YT = "yt"
        const val SOURCE_YT_ARTIST = "yt_artist"
        const val SOURCE_LASTFM = "lastfm"
        const val SOURCE_MANUAL = "manual"
    }
}

/** 記錄每首歌上次自動分類的時間，避免每天重查同一首。 */
@Immutable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Song::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongTagState(
    @PrimaryKey val songId: String,
    val taggedAt: Long,
    val sources: String
)
