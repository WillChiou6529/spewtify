package app.vitune.providers.innertube.models.bodies

import app.vitune.providers.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context = Context.DefaultWeb,
    val browseId: String,
    val params: String? = null,
    /** 排行榜選國家用（FEmusic_charts）：selectedValues = ["TW"]、["ZZ"]（全球）… */
    val formData: FormData? = null
) {
    @Serializable
    data class FormData(val selectedValues: List<String>)
}
