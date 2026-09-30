package dev.reelscounter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.reelscounter.app.ui.AppRoot
import dev.reelscounter.app.ui.theme.ReelsTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ReelsTheme {
        AppRoot()
      }
    }
  }
}
