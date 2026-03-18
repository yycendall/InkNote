//package com.assistant.inknote
//
//import com.assistant.inknote.R.string.developToken
//import com.evernote.auth.EvernoteAuth
//import com.evernote.clients.ClientFactory
//import com.evernote.clients.NoteStoreClient
//import java.util.concurrent.locks.ReentrantLock
//
//interface InkNoteApi {
//
//    fun getNotes(): List<Note>
//
//    //fun getNote(): Note
//
//    fun listNotebooks(): List<NoteBook>
//
//}
//
//
//class InkNoteApiImpl :InkNoteApi{
//
//    private lateinit var noteClient: NoteStoreClient
//    private var noteList = ArrayList<Note>()
//    private var noteBookList = ArrayList<NoteBook>()
//
//    var lock = ReentrantLock()
//
//    private fun initNoteClient(developToken:String): NoteStoreClient {
//        if (!::noteClient.isInitialized) {
//            var evernoteAuth = EvernoteAuth(
//                com.evernote.auth.EvernoteService.YINXIANG,
//                developToken
//            )
//            var factory = ClientFactory(evernoteAuth)
//            noteClient = factory.createNoteStoreClient()
//        }
//        return noteClient
//    }
//
//
//    override fun getNotes(): List<Note>{
//        return  noteList
//    }
//
//    override fun listNotebooks(): List<NoteBook>{
//
//        try {
//            lock.lock()
//
//
//
//        }finally {
//            lock.unlock()
//        }
//
//        return noteBookList
//    }
//
//    private fun searchNotes(bookName: String) {
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                lock.lock()
//
//                if (bookName.isEmpty()||bookName.isBlank()){
//                    bookguid = ""
//                }else{
//                    if (noteBookList.isEmpty()) {
//                        noteBookList.addAll(noteClient.listNotebooks())
//                    }
//
//                    for (book in noteBookList) {
//                        if (book.name.contains(bookName)) {
//                            bookguid = book.guid
//                            break
//                        } else {
//                            bookguid = ""
//                        }
//                    }
//                }
//                noteList.clear()
//                getNotes(0, fetchSize, bookguid)
//
//                CoroutineScope(Main).launch {
//                    showCurrentPage()
//                }
//
//
//
//            }catch (e: Exception){
//
//            }finally {
//                lock.unlock()
//            }
//        }
//    }
//}
//
//data class NoteListResponse(
//    val notes: List<Note>
//)
//
