
package com.ttpredictor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var matches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var apiUrl by remember { mutableStateOf("") } // leave empty to use local asset

    LaunchedEffect(apiUrl) {
        loading = true
        error = null
        matches = emptyList()
        try {
            val result = if (apiUrl.isBlank()) {
                // Read local asset
                withContext(Dispatchers.IO) {
                    val input = MyApp.context.assets.open("sample_matches.json")
                    BufferedReader(InputStreamReader(input)).use { it.readText() }
                }
            } else {
                val client = HttpClient(Android) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
                client.get(apiUrl).bodyAsText()
            }
            val payload = Json { ignoreUnknownKeys = true }.decodeFromString(ApiPayload.serializer(), result)
            matches = payload.matches.map { it.withPrediction() }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("TT Prudent Predictor") })
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (error != null) {
                Text("Eroare: $error", color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(matches) { m -> MatchCard(m) }
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Sfat: Lasă API URL gol pentru a folosi datele locale. Când backend-ul este gata, setează aici adresa API.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatchCard(m: Match) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${m.playerA} vs ${m.playerB}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(m.competition + " • " + m.startTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Total estimat: ${m.prediction.totalEstimated} (interval ${m.prediction.low} – ${m.prediction.high})")
            Text("Verdict: ${m.prediction.verdict} • Încredere: ${(m.prediction.confidence * 100).roundToInt()}%")

            LinearProgressIndicator(
                progress = { m.prediction.confidence.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------- Models & Prediction ----------

@Serializable
data class ApiPayload(val date: String, val matches: List<Match>)

@Serializable
data class Match(
    val matchId: String,
    val competition: String,
    val startTime: String,
    val playerA: String,
    val playerB: String,
    val last10: Last10,
    val prediction: Prediction? = null
) {
    fun withPrediction(): Match {
        val pred = computePrediction(last10)
        return this.copy(prediction = pred)
    }
}

@Serializable
data class Last10(
    val playerA: List<PlayedMatch>,
    val playerB: List<PlayedMatch>
)

@Serializable
data class PlayedMatch(val sets: List<List<Int>>)

@Serializable
data class Prediction(
    val totalEstimated: Int,
    val low: Int,
    val high: Int,
    val verdict: String,
    val confidence: Double
)

fun computePrediction(last10: Last10): Prediction {
    fun totals(list: List<PlayedMatch>): Pair<Double, Double> {
        // returns (mean total points per match, std-like dispersion proxy)
        val totals = list.map { pm ->
            pm.sets.sumOf { it[0] + it[1] }.toDouble()
        }
        val mean = if (totals.isEmpty()) 0.0 else totals.average()
        val variance = if (totals.size <= 1) 0.0 else totals.map { (it - mean) * (it - mean) }.sum() / (totals.size - 1)
        val std = kotlin.math.sqrt(variance)
        return Pair(mean, std)
    }

    val (meanA, stdA) = totals(last10.playerA.take(10))
    val (meanB, stdB) = totals(last10.playerB.take(10))

    // Combine means with more weight on the last 3 matches if available
    fun weightedMean(list: List<PlayedMatch>): Double {
        val n = min(10, list.size)
        if (n == 0) return 76.0 // fallback
        var wsum = 0.0
        var s = 0.0
        for (i in 0 until n) {
            val total = list[i].sets.sumOf { it[0] + it[1] }.toDouble()
            val w = if (i < 3) 1.6 else 1.0 // last 3 (index 0..2) more weight, assuming array is in reverse-chronological
            s += w * total
            wsum += w
        }
        return s / wsum
    }

    val wmA = weightedMean(last10.playerA)
    val wmB = weightedMean(last10.playerB)

    // Expected total as average of the two players' own match totals
    val exp = (wmA + wmB) / 2.0

    // Confidence inversely related to dispersion
    val disp = (stdA + stdB) / 2.0
    val confidence = (1.0 - (disp / 20.0)).coerceIn(0.4, 0.9) // clamp

    val low = max(60.0, exp - 8.0).roundToInt()
    val high = min(120.0, exp + 8.0).roundToInt()
    val totalEstimated = exp.roundToInt()

    val verdict = when {
        totalEstimated >= 78 -> "Peste 74.5"
        totalEstimated <= 72 -> "Sub 74.5"
        else -> "Evită / Live"
    }
    return Prediction(totalEstimated, low, high, verdict, confidence)
}

// Provide application context for assets
object MyApp {
    lateinit var context: android.content.Context
}
