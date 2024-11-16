package dev.schlaubi.discordvoicestats

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.seconds

suspend fun <T, R> List<T>.parallelMap(
    maxParallelism: Int = 5,
    maxRequestsPerSecond: Int = 1000,
    transform: suspend CoroutineScope.(T) -> R
): List<R> {
    val semaphore = Semaphore(maxParallelism)
    val rateLimiterChannel = Channel<Unit>(Channel.RENDEZVOUS)

    return coroutineScope {
        // Launch a separate coroutine for rate limiting
        val rateLimiterJob = launch {
            while (isActive) {
                repeat(maxRequestsPerSecond) {
                    rateLimiterChannel.send(Unit)
                }
                delay(1.seconds)
            }
        }

        val results = map { item ->
            async {
                rateLimiterChannel.receive()
                semaphore.withPermit {
                    transform(item)
                }
            }
        }.awaitAll()

        rateLimiterJob.cancelAndJoin() // Ensure rate limiter job completes

        results
    }
}
