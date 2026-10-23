package com.hermes.mobile.domain.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("assets") val assets: List<ReleaseAsset> = emptyList()
)

@Serializable
data class ReleaseAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("size") val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null
)

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val asset: ReleaseAsset
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class NoUpdateAvailable(val currentVersion: String) : UpdateState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progressPercent: Int) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apkPath: String) : UpdateState
    data class Error(val message: String) : UpdateState
}
