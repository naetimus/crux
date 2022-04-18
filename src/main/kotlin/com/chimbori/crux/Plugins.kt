package com.chimbori.crux

import com.chimbori.crux.Fields.AMP_URL
import com.chimbori.crux.Fields.BANNER_IMAGE_URL
import com.chimbori.crux.Fields.CANONICAL_URL
import com.chimbori.crux.Fields.DESCRIPTION
import com.chimbori.crux.Fields.FAVICON_URL
import com.chimbori.crux.Fields.FEED_URL
import com.chimbori.crux.Fields.KEYWORDS_CSV
import com.chimbori.crux.Fields.SITE_NAME
import com.chimbori.crux.Fields.THEME_COLOR_HEX
import com.chimbori.crux.Fields.TITLE
import com.chimbori.crux.Fields.VIDEO_URL
import com.chimbori.crux.articles.extractAmpUrl
import com.chimbori.crux.articles.extractCanonicalUrl
import com.chimbori.crux.articles.extractDescription
import com.chimbori.crux.articles.extractFaviconUrl
import com.chimbori.crux.articles.extractFeedUrl
import com.chimbori.crux.articles.extractImageUrl
import com.chimbori.crux.articles.extractKeywords
import com.chimbori.crux.articles.extractSiteName
import com.chimbori.crux.articles.extractThemeColor
import com.chimbori.crux.articles.extractTitle
import com.chimbori.crux.articles.extractVideoUrl
import com.chimbori.crux.common.cruxOkHttpClient
import com.chimbori.crux.common.fromUrl
import com.chimbori.crux.common.nullIfBlank
import com.chimbori.crux.urls.isLikelyArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * An ordered list of default plugins configured in Crux. Callers can override and provide their own list, or pick and
 * choose from the set of available default plugins to create their own configuration.
 */
public val DEFAULT_PLUGINS: List<Plugin> = listOf(
  AmpPlugin(refetchContentFromCanonicalUrl = true),
  HtmlMetadataPlugin()  // Fallback extractor that parses many standard HTML attributes.
)

/**
 * Extracts common well-defined metadata fields from an HTML DOM tree. Includes support for:
 * - Twitter Cards Metadata: https://developer.twitter.com/en/docs/twitter-for-websites/cards/overview/markup
 * - Open Graph Protocol: https://ogp.me/
 * - AMP Spec: https://amp.dev/documentation/guides-and-tutorials/learn/spec/amphtml/
 */
public class HtmlMetadataPlugin : Plugin {
  /** Skip handling any file extensions that are unlikely to be HTML pages. */
  public override fun canHandle(url: HttpUrl): Boolean = url.isLikelyArticle()

  override suspend fun handle(request: Resource): Resource = withContext(Dispatchers.IO) {
    val canonicalUrl: HttpUrl? = request.document?.extractCanonicalUrl()?.let {
      request.url?.resolve(it)
    } ?: request.url

    Resource(
      fields = mapOf(
        TITLE to request.document?.extractTitle(),
        CANONICAL_URL to request.document?.extractCanonicalUrl(),
        DESCRIPTION to request.document?.extractDescription(),
        SITE_NAME to request.document?.extractSiteName(),
        THEME_COLOR_HEX to request.document?.extractThemeColor(),
        KEYWORDS_CSV to request.document?.extractKeywords()?.joinToString(separator = ","),
      ),
      urls = mapOf(
        FAVICON_URL to request.document?.extractFaviconUrl(canonicalUrl),
        BANNER_IMAGE_URL to request.document?.extractImageUrl(canonicalUrl),
        FEED_URL to request.document?.extractFeedUrl(canonicalUrl),
        AMP_URL to request.document?.extractAmpUrl(canonicalUrl),
        VIDEO_URL to request.document?.extractVideoUrl(canonicalUrl),
      )
    ).removeNullValues()
  }
}

/**
 * If the current page is an AMP page, then [AmpPlugin] extracts the canonical URL & replaces the DOM tree for the AMP
 * page with the DOM tree for the canonical page.
 */
public class AmpPlugin(
  private val refetchContentFromCanonicalUrl: Boolean,
  private val okHttpClient: OkHttpClient = cruxOkHttpClient
) : Plugin {
  /** Skip handling any file extensions that are unlikely to be an HTML page. */
  override fun canHandle(url: HttpUrl): Boolean = url.isLikelyArticle()

  override suspend fun handle(request: Resource): Resource? {
    request.document?.select("link[rel=canonical]")?.attr("href")?.nullIfBlank()?.let {
      return Resource.fromUrl(
        url = it.toHttpUrl(),
        shouldFetchContent = refetchContentFromCanonicalUrl,
        okHttpClient = okHttpClient
      )
    }
    return null
  }
}