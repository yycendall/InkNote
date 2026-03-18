package com.assistant.inknote

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.assistant.inknote.databinding.ActivityNoteListBinding
import com.evernote.auth.EvernoteAuth
import com.evernote.auth.EvernoteService
import com.evernote.clients.ClientFactory
import com.evernote.clients.NoteStoreClient
import com.evernote.edam.notestore.NoteFilter
import com.evernote.edam.type.NoteSortOrder
import com.evernote.edam.notestore.NotesMetadataResultSpec
import com.evernote.edam.type.Notebook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.jvm.java
import java.util.*

class NoteListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteListBinding
    private lateinit var noteListAdapter: NoteListAdapter
    private lateinit var searchEditText: EditText

    private lateinit var noteClient: NoteStoreClient

    private var noteList = ArrayList<Note>()
    private var noteBookList = mutableListOf<Notebook>()
    private var currentPageNotes = ArrayList<Note>()

    private var searchQuery = ""

    private var currentPage = 0
    private var pageSize = 18 // 每页显示的笔记数量
    private var fetchSize = 50
    private var bookguid = ""

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels.toInt()
        val sdkVersion = Build.VERSION.SDK_INT

        when (sdkVersion) {
            Build.VERSION_CODES.R -> {
                if (screenHeight == 1648) pageSize = 15
                if (screenHeight == 1872) pageSize = 18
            }

            Build.VERSION_CODES.P -> pageSize = 19
        }

        CoroutineScope(Dispatchers.IO).launch {
            getNotesByBookId(0, fetchSize)
            CoroutineScope(Main).launch {
                showCurrentPage()
            }
        }

        initNoteListAdapter()
        initButtonUpDownListener()
        initSearchListener()
    }


    private fun initNoteClient() {
        val savedToken = sharedPreferences.getString("evernote_token", null)
        try {
            if (!::noteClient.isInitialized) {
                val evernoteAuth = if (savedToken != null) {
                    EvernoteAuth(
                        EvernoteService.YINXIANG,
                        savedToken
                    )
                } else {
                    EvernoteAuth(
                        EvernoteService.YINXIANG,
                        getString(R.string.developToken)
                    )
                }
                val factory = ClientFactory(evernoteAuth)
                noteClient = factory.createNoteStoreClient()
            }
        } catch (e: Exception) {
            CoroutineScope(Dispatchers.Main).launch {
                showTokenInputDialog()
            }
        }
    }


    private fun showTokenInputDialog() {
        try {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("输入印象笔记Token")

            // 创建一个垂直的线性布局，用于容纳输入框和文本视图
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(20, 20, 20, 20)

            // 输入框
            val input = EditText(this)
            layout.addView(input)

            // 要显示的文字和对应的 URL
            val linkText = "印象笔记token生成"
            val url = "https://app.yinxiang.com/api/DeveloperToken.action"

            // 创建 SpannableString 并设置点击事件
            val spannableString = SpannableString(linkText)
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(view: View) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }
            }
            spannableString.setSpan(
                clickableSpan,
                0,
                linkText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 创建 TextView 并设置文本和点击事件
            val textView = TextView(this)
            textView.text = spannableString
            textView.movementMethod = LinkMovementMethod.getInstance()
            layout.addView(textView)

            // 设置布局到对话框
            builder.setView(layout)

            builder.setPositiveButton("确定") { _, _ ->
                val token = input.text.toString()
                if (token.isNotEmpty()) {
                    sharedPreferences.edit().putString("evernote_token", token).apply()
                    CoroutineScope(Dispatchers.IO).launch {
                        getNotesByBookId(0, fetchSize)
                        CoroutineScope(Main).launch {
                            showCurrentPage()
                        }
                    }
                }
            }
            builder.setNegativeButton("取消", null)
            builder.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun getNotesByQuery(offset: Int, maxNotes: Int, queryWords: String = "") {
        bookguid = ""
        getNotes(offset, maxNotes, "", queryWords)
    }

    @Synchronized
    private fun getNotesByBookId(offset: Int, maxNotes: Int, bookguid: String = "") {
        getNotes(offset, maxNotes, bookguid)
    }

    @Synchronized
    private fun getNotes(
        offset: Int,
        maxNotes: Int,
        bookguid: String = "",
        queryWords: String = ""
    ) {
        initNoteClient()
        if (!::noteClient.isInitialized) {
            return
        }

        val noteFilter = NoteFilter()
        noteFilter.notebookGuid = bookguid
        noteFilter.order = NoteSortOrder.UPDATED.getValue() // 按更新时间排序
        noteFilter.words = queryWords
        noteFilter.setAscending(false) // 降序排列
        val resultSpec = NotesMetadataResultSpec()
        resultSpec.setIncludeTitle(true)

        var result = noteClient.findNotesMetadata(noteFilter, offset, maxNotes, resultSpec)

        for (yinxiangNote in result.notes) {
            var n = Note(yinxiangNote.guid, yinxiangNote.title, "")
            noteList.add(n)
        }
    }

    private fun initButtonUpDownListener() {
        val buttonUp = findViewById<Button>(R.id.buttonUp)
        val buttonDown = findViewById<Button>(R.id.buttonDown)

        buttonUp.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                showCurrentPage()
            }
        }
        // 设置按钮 B 的点击事件
        buttonDown.setOnClickListener {
            if ((currentPage + 1) * pageSize < noteList.size) {
                currentPage++
                showCurrentPage()
            }
            if (noteList.size - (currentPage + 1) * pageSize < pageSize) {
                CoroutineScope(Dispatchers.IO).launch {
                    if (searchQuery.startsWith("@")) {
                        getNotesByBookId(noteList.size, fetchSize, bookguid)
                    } else {
                        getNotesByQuery(noteList.size, fetchSize, searchQuery)
                    }
                }
            }
        }
    }


    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun initSearchListener() {
        searchEditText = findViewById(R.id.searchEditText)
        searchEditText.setOnEditorActionListener(object : TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard()
                    noteList.clear()
                    searchQuery = searchEditText.text.toString()
                    searchNotes(searchQuery)

                    searchEditText.clearFocus()
                    return true
                }
                searchEditText.clearFocus()
                return false
            }
        })
    }

    @Synchronized
    private fun searchNotes(query: String) {

        currentPage = 0
        noteList.clear()

        CoroutineScope(Dispatchers.IO).launch {
            if (query.isEmpty() || query.isBlank()) {
                bookguid = ""
                getNotesByBookId(0, fetchSize, bookguid)
            } else {
                if (query.startsWith("@")) {
                    if (noteBookList.isEmpty()) {
                        noteBookList.addAll(noteClient.listNotebooks())
                    }
                    for (book in noteBookList) {
                        if (book.name.contains(query.substring(1))) {
                            bookguid = book.guid
                            break
                        } else {
                            bookguid = ""
                        }
                    }
                    getNotesByBookId(0, fetchSize, bookguid)
                } else {
                    getNotesByQuery(0, fetchSize, query)
                }
            }
            CoroutineScope(Main).launch {
                showCurrentPage()
            }
        }
    }


    private fun showCurrentPage() {
        val startIndex = currentPage * pageSize
        val endIndex =
            if (startIndex + pageSize > noteList.size) noteList.size else startIndex + pageSize
        currentPageNotes.clear()
        currentPageNotes.addAll(noteList.subList(startIndex, endIndex))
        noteListAdapter.notifyDataSetChanged()
    }


    private fun initNoteListAdapter() {
        noteListAdapter = NoteListAdapter(currentPageNotes) { note ->
            // 点击笔记进入详情页
            val intent = Intent(NoteListActivity@ this, NoteDetailActivity::class.java)
            intent.putExtra("note", note)
            intent.putParcelableArrayListExtra("noteList", noteList)
            startActivity(intent)
        }
        binding.noteListRecyclerView.adapter = noteListAdapter
        binding.noteListRecyclerView.layoutManager = LinearLayoutManager(this)
    }
}
