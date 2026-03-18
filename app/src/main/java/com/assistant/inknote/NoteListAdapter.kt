package com.assistant.inknote

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

import com.assistant.inknote.databinding.ItemNoteBinding
import org.jsoup.Jsoup


class NoteListAdapter(private val noteList: List<Note>, private val onNoteClick: (Note) -> Unit) :
    RecyclerView.Adapter<NoteListAdapter.NoteViewHolder>() {

    // 创建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return this.NoteViewHolder(binding)
    }

    // 绑定数据到 ViewHolder
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = noteList[position]
        holder.binding.noteTitle.text = note.title
        //holder.binding.noteOverview.text = convertEnmlToText(note.content)

        holder.binding.root.setOnClickListener { onNoteClick(note) }
    }

    // 返回列表项数量
    override fun getItemCount(): Int {
        return noteList.size
    }
    fun convertEnmlToText(enml:String): String {
        // 移除 ENML 的 XML 声明和 DTD 声明
        var processedEnml = enml.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "")
        processedEnml = processedEnml.replace("<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">", "")
        // 使用 Jsoup 解析 ENML
        val doc = Jsoup.parse(processedEnml)
        return doc.wholeText()
    }
    // ViewHolder 类
    inner class NoteViewHolder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)
}
