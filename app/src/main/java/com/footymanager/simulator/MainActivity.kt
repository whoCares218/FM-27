package com.footymanager.simulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.footymanager.simulator.ui.FootballManagerApp
import com.footymanager.simulator.viewmodel.GameViewModel

/**
 * Single activity host. The app is entirely Compose; configuration changes are
 * handled by the ViewModel, which survives rotation with the career intact.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                val gameViewModel: GameViewModel = viewModel()
                FootballManagerApp(viewModel = gameViewModel)
            }
        }
    }
}
