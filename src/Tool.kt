package dev.schlaubi.discordvoicestats

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.encodeToStream
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.io.path.Path
import kotlin.io.path.outputStream

@Serializable
data class Certificate(
    val issuerCaId: Int, val issuerName: String, val commonName: String, val nameValue: String,
    val id: Long,
    val entryTimestamp: String,
    val notBefore: LocalDateTime,
    val notAfter: LocalDateTime,
    val serialNumber: String,
    val resultCount: Int
)

@Serializable
data class IPInfo(
    val ip: String,
    val hostname: String? = null,
    val anycast: Boolean = false,
    val bogon: Boolean = false,
    val city: String,
    val region: String,
    val country: String,
    val loc: String,
    val org: String,
    val postal: String? = null,
    val timezone: String
)

@OptIn(ExperimentalSerializationApi::class)
val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
}
private val dnsCache = Cache(File("cache", "dns_cache"), 10 * 1024 * 1024)
private val okHttpClient = OkHttpClient.Builder()
    .cache(dnsCache)
    .build()
private val dns = DnsOverHttps.Builder()
    .client(okHttpClient)
    .url("https://dns.quad9.net/dns-query".toHttpUrl())
    .bootstrapDnsHosts(InetAddress.getByName("9.9.9.9"), InetAddress.getByName("149.112.112.112"))
    .build()

private val domainPattern = "[a-z]+[0-9]+\\.discord\\.gg".toRegex()

private val client = HttpClient(OkHttp) {
    engine {
        preconfigured = okHttpClient
    }
    install(ContentNegotiation) {
        json(json)
    }

    install(HttpRequestRetry)
}

@Serializable
private data class DNSAnswer(
    @SerialName("Answer")
    val answer: List<Answer> = emptyList()
) {
    @Serializable
    data class Answer(
        val name: String,
        val data: String
    )
}

@Serializable
data class CertificateWithIP(
    val ip: String,
    val certificate: Certificate
)

@Serializable
data class CertificateWithIPData(
    val ipInfo: IPInfo,
    val certificate: Certificate
)

suspend fun main() = client.use {
    var i = 1
    val domainNamesResponse = client.get("https://crt.sh/json?q=discord.gg&exclude=expired") {
        retry {
            maxRetries = 5
            exponentialDelay()
        }
    }
    if (!domainNamesResponse.status.isSuccess()) {
        error("Could not fetch domain list: ${domainNamesResponse.status}")
    }
    val input = domainNamesResponse.body<List<Certificate>>()
        .filter { it.commonName.matches(domainPattern) }

    val withIp = input.parallelMap(maxRequestsPerSecond = 500, maxParallelism = 50) { certificate ->
        println("IP Mapping: ${certificate.commonName} ${i++}/${input.size}")

        val ip = try {
            blocking {
                dns.lookup(certificate.commonName).firstOrNull()?.hostAddress
            }
        } catch (_: UnknownHostException) {
            println("Could not resolve name: ${certificate.commonName}")
            null
        }
        ip?.let { CertificateWithIP(ip, certificate) }
    }.filterNotNull()

    val domainMap = withIp.associate { it.ip to it.certificate }

    val batches = withIp.chunked(1000).flatMap {
        client.post("https://ipinfo.io/batch") {
            url.parameters.append("token", Config.IP_INFO_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(it.map { it.ip })
        }.body<Map<String, IPInfo>>().values
    }.map {
        CertificateWithIPData(it, domainMap.getValue(it.ip))
    }

    val response = client.put(Config.API_SERVER) {
        url.appendEncodedPathSegments("servers")

        setBody(batches)
    }

    println("API Response: ${response.status}")
}
