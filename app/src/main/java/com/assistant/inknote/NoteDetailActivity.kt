package com.assistant.inknote

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.assistant.inknote.databinding.ActivityNoteDetailBinding
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.evernote.auth.EvernoteAuth
import com.evernote.client.android.EvernoteSession
import com.evernote.client.android.EvernoteSession.EvernoteService
import com.evernote.client.android.asyncclient.EvernoteNoteStoreClient
import com.evernote.clients.ClientFactory
import com.evernote.clients.NoteStoreClient
import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.NoteSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.xml.sax.XMLReader
import java.util.Stack
import java.util.regex.Pattern

class NoteDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var note: Note
    private var noteMetaList = mutableListOf<Note>()
    private var noteMap = HashMap<String, Note>()
    private var pageContents: MutableList<String> = mutableListOf()
    private var currentIndex = 0
    private var currentPage = 0

    val titlePxSize = 30

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var noteClient: NoteStoreClient

    private fun hideScreenBar(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        hideScreenBar()

        note = intent.getParcelableExtra("note")!!
        noteMetaList = intent.getParcelableArrayListExtra("noteList")!!
        currentIndex = noteMetaList.indexOf(note)


        initNote(note.id)

//        CoroutineScope(Dispatchers.IO).launch {
//
//
//        }


        binding.noteContentScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    val x = event.x
                    val y = event.y
                    val width = resources.displayMetrics.widthPixels
                    val height = resources.displayMetrics.heightPixels
                    if (x > width * 0.7) {
                        // 点击右侧，向下翻页
                        scrollDown()
                    } else if (x < width * 0.3) {
                        // 点击左侧，向上翻页
                        scrollUp()
                    } else if (y < height * 0.3) {
                        // 点击上侧，进入上一条笔记
                        navigateToPreviousNote()
                    } else if (y > height * 0.7) {
                        // 点击下侧，进入下一条笔记
                        navigateToNextNote()
                    }
                }
            }
            false
        }
    }

    private fun initNoteClient(): NoteStoreClient {

        val token = sharedPreferences.getString("evernote_token", null)

        if (!::noteClient.isInitialized) {
            var evernoteAuth = EvernoteAuth(
                com.evernote.auth.EvernoteService.YINXIANG,
                token)
            var factory = ClientFactory(evernoteAuth)
            noteClient = factory.createNoteStoreClient()
        }
        return noteClient
    }

    private fun initNote(id: String, begin: Boolean = true) {

        currentPage = 0
        pageContents.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            try {

                noteClient = initNoteClient()
                var localNote = noteMap.get(id)
                if (localNote == null) {

                    val evernoteNote = noteClient.getNote(id, true, false, false, false)
                    localNote = Note(evernoteNote.guid, evernoteNote.title, evernoteNote.content)
                    noteMap.put(localNote.id, localNote)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    for (i in 0 until noteMetaList.size - 1) {
                        if (noteMetaList[i].id == id) {
                            var nextid = noteMetaList[i + 1].id
                            var nextNote = noteMap.get(nextid)
                            if (nextNote == null) {
                                val nextEvernoteNote = noteClient.getNote(nextid, true, false, false, false)
                                nextNote = Note(nextEvernoteNote.guid, nextEvernoteNote.title, nextEvernoteNote.content)
                                noteMap.put(nextNote.id, nextNote)
                            }
                            break
                        }
                    }
                }


                // 切换到主线程更新 UI
                lifecycleScope.launch(Dispatchers.Main) {
                    handleNote(localNote)
                    calculatePages(binding.noteContentTextView)
                    if (begin) {
                        goToPage(0)
                    } else {
                        goToPage(pageContents.size - 1)
                    }

                }
            } catch (e: Exception) {
                // 切换到主线程显示错误信息
                lifecycleScope.launch(Dispatchers.Main) {
                    showError(e.message ?: "获取笔记失败")
                }
            }
        }

    }

    private fun scrollDown() {
        if (currentPage == pageContents.size - 1) {
            navigateToNextNote()
        } else {
            binding.noteContentScrollView.post(Runnable {
                goToNextPage()
            })
        }
    }

    private fun scrollUp() {
        if (currentPage == 0) {
            navigateToPreviousNote(false)
        } else {
            binding.noteContentScrollView.post(Runnable {
                goToPrevPage()
            })
        }
    }

    private fun navigateToNextNote() {
        if (currentIndex < noteMetaList.size - 1) {
            currentIndex++
            initNote(noteMetaList[currentIndex].id)
            binding.noteContentScrollView.post(Runnable {
                binding.noteContentScrollView.scrollTo(0, 0)
            })




        }
    }

    private fun navigateToPreviousNote(begin: Boolean = true) {


        if (currentIndex > 0) {
            currentIndex--
        }
        initNote(noteMetaList[currentIndex].id, begin)

        binding.noteContentScrollView.post(Runnable {
            binding.noteContentScrollView.scrollTo(0, 0)
        })

    }

    private fun handleNote(currentNote: Note) {
        // 处理获取到的笔记，例如显示在界面上
        binding.noteTitleView.text = currentNote.title

        var spanned: Spanned =
            Html.fromHtml(convertEnmlToHtml(currentNote), Html.FROM_HTML_MODE_COMPACT)
        var spannableString = SpannableString(spanned)

        val newText = replaceHyphensInString(spannableString.toString())
        spannableString = SpannableString(newText)

        binding.noteContentTextView.text = spannableString


    }


var charC = '-'
var charS = "-"

    fun replaceHyphensInString(input: String): String {
        val lines = input.lines()
        val processedLines = mutableListOf<String>()

        for (line in lines) {
            var hyphenCount = 0
            var currentIndex = 0

            // 统计开头连续的 - 字符数量
            while (currentIndex < line.length && line[currentIndex] == charC) {
                hyphenCount++
                currentIndex++
            }
            var processedLine:String=""

            if(hyphenCount>0){
                // 生成替换后的前缀
                val tabPrefix = "\t\t".repeat(hyphenCount) + charC

                // 拼接替换后的前缀和剩余的文本
                processedLine = tabPrefix + line.drop(hyphenCount).trimStart()
            }else{
                processedLine=line
            }
            processedLines.add(processedLine)
        }

        // 将处理后的行重新组合成字符串
        return processedLines.joinToString("\n")
    }


    private fun dpToPx(dp: Int): Int {
        val density = this.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    fun processListElements(parent: Element, depth: Int) {
        val ulElements = parent.getElementsByTag("ul")
        for (ul in ulElements) {
            val liElements = ul.getElementsByTag("li")
            for (li in liElements) {
                val prefix = charS.repeat(depth + 1)
                li.text("$prefix${li.text().trim()}")
                // 递归处理嵌套的 <ul>
                processListElements(li, depth + 1)
            }
        }
    }

    fun convertEnmlToHtml(note: Note): String {
        // 移除 ENML 的 XML 声明和 DTD 声明
        var processedEnml = note.content.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "")
        processedEnml = processedEnml.replace(
            "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">", ""
        )
        // 使用 Jsoup 解析 ENML
        val doc = Jsoup.parse(processedEnml)

        processListElements(doc,0)

//        val paragraphs: List<Element> = doc.select("li")
//
//        for (paragraph in paragraphs) {
//
//
//            // 获取段落的文本内容
//            var text = paragraph.text()
//            // 去除文本中的换行符
//            text = " - " + text
//            // 更新段落的文本内容
//            paragraph.text(text)
//        }
        return doc.html()
    }


    private fun calculatePages(textView: TextView) {
        val layout: Layout = textView.layout ?: return
        var totalHeight = layout.height
        var pageHeight = textView.height
        var scrollY = 0

        val rect = Rect()
        textView.getGlobalVisibleRect(rect)
        pageHeight = rect.height()
        Log.d("VisibleHeight", "可视区域高度: $pageHeight")

        while (scrollY < totalHeight) {
            textView.scrollY = scrollY
            val visibleText = getVisibleText(textView)
            pageContents.add(visibleText)
            scrollY += resources.displayMetrics.heightPixels - titlePxSize - 120
        }
    }

    private fun getVisibleText(textView: TextView): String {
        val layout: Layout = textView.layout ?: return ""
        val visibleTop = textView.scrollY
        val visibleBottom = visibleTop + resources.displayMetrics.heightPixels - titlePxSize -200
        var firstVisibleLine = layout.getLineForVertical(visibleTop)
//        if(firstVisibleLine>0){
//            firstVisibleLine = firstVisibleLine-2
//        }
        var lastVisibleLine = layout.getLineForVertical(visibleBottom)
//        if(lastVisibleLine>0){
//            lastVisibleLine = lastVisibleLine-1
//        }
        val sb = StringBuilder()
        for (i in firstVisibleLine..lastVisibleLine) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            sb.append(textView.text, start, end)
            if (i < lastVisibleLine) {
                sb.append("")
            }
        }
        return sb.toString()
    }

    fun goToPage(pageIndex: Int) {
        if (pageIndex in 0 until pageContents.size) {
            currentPage = pageIndex
            binding.noteContentTextView.text = pageContents[currentPage]
        }
    }

    fun goToNextPage() {
        if (currentPage < pageContents.size - 1) {
            currentPage++
            binding.noteContentTextView.text = pageContents[currentPage]
        }
    }

    fun goToPrevPage() {
        if (currentPage > 0) {
            currentPage--
            binding.noteContentTextView.text = pageContents[currentPage]
        }
    }


}

class MyTagHandler(private val context: Context) : Html.TagHandler {
    private val stack = Stack<Boolean>()

    override fun handleTag(
        opening: Boolean,
        tag: String,
        output: Editable,
        xmlReader: XMLReader
    ) {
        print(tag)
        when (tag.lowercase()) {
            "en-note" -> {
                // 可以在这里添加对 en-note 标签的特殊处理，如果不需要特殊处理，直接跳过
            }
            "ul" -> {
                if (opening) {
                    stack.push(true)
                } else {
                    stack.pop()
                }
            }
            "li" -> {
                if (opening) {
                    if (stack.isNotEmpty() && stack.peek()) {
                        output.append(" - ")
                    }
                } else {
                    val start = output.length - (output.toString().substringAfterLast(" - ").length)
                    val leadingMarginSpan = LeadingMarginSpan.Standard(0, dpToPx(20))
                    output.setSpan(leadingMarginSpan, start, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            else -> {
                print(tag)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
