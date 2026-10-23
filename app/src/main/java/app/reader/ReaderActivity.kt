package app.reader

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import app.reader.data.Book

class ReaderActivity : FragmentActivity() {
    companion object {
        const val EXTRA_KEY = "book.key"
        fun intent(context: Context, book: Book) = Intent(context, ReaderActivity::class.java).putExtra(EXTRA_KEY, book.key)
    }
}
