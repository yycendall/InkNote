package com.assistant.inknote


import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

// 公众号文章爬虫类
class FetchArticle(private val publicIds: List<String>) {
    private val client = OkHttpClient()

    // 抓取最近5篇文章的正文内容
    fun crawlArticles(): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        for (publicId in publicIds) {
            val articleUrls = getArticleUrls(publicId)
            val articleContents = mutableListOf<String>()
            for (url in articleUrls.take(1)) {
                val content = getArticleContent(url)
                if (content.isNotEmpty()) {
                    articleContents.add(content)
                }
            }
            result[publicId] = articleContents
        }
        return result
    }

    // 获取公众号文章列表的URL
    private fun getArticleUrls(publicId: String): List<String> {
        // 这里需要根据实际情况构造请求URL和处理登录状态
        val url = "https://mp.weixin.qq.com/xxx?publicId=$publicId"
        val request = Request.Builder()
            .url(url)
            .build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseData = response.body?.string() ?: ""
                val doc = Jsoup.parse(responseData)
                // 这里需要根据实际HTML结构修改选择器
                val articleLinks = doc.select("a.article-link")
                return articleLinks.map { it.attr("href") }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return emptyList()
    }

    // 获取文章正文内容
    private fun getArticleContent(url: String): String {
        val request = Request.Builder()
            .url(url)
            .build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseData = response.body?.string() ?: ""
                val doc = Jsoup.parse(responseData)
                // 这里需要根据实际HTML结构修改选择器
                val contentElement = doc.selectFirst("div.article-content")
                return contentElement?.text() ?: ""
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return ""
    }
}

fun main() {
    val publicIds = listOf("publicId1",)
    val crawler = FetchArticle(publicIds)
    val articles = crawler.crawlArticles()
    for ((publicId, contents) in articles) {
        println("公众号ID: $publicId")
        for ((index, content) in contents.withIndex()) {
            println("第 ${index + 1} 篇文章内容: $content")
        }
    }
}
