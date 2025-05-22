package app.ct5.desky

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class StartMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.start_layout)
        handleCloseIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCloseIntent(intent)
    }

    private fun handleCloseIntent(intent: Intent) {
        if (intent.getBooleanExtra("close", false)) {
            finish()
        }
    }
}

