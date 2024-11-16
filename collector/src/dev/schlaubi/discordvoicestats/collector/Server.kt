package dev.schlaubi.discordvoicestats.collector

import dev.schlaubi.discordvoicestats.Certificate
import dev.schlaubi.discordvoicestats.CertificateWithIPData
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(Server::class.java)

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
}

class Server(private val database: Database) {

    fun init() = embeddedServer(Netty, 8080) {
        install(ContentNegotiation) {
            json(json)
        }

        routing {
            route()
        }
    }

    private fun Routing.route() {
        put("servers") {
            val token = call.request.authorization()
            if (token != Config.TOKEN) {
                return@put call.respond(HttpStatusCode.Unauthorized)
            }
            val servers = call.receive<List<CertificateWithIPData>>()
            blocking {
                database.transactionWithResult {
                    servers.forEach {
                        log.debug("Up-serting certificate: {}", it.certificate.serialNumber)
                        database.functionsQueries.upsertCertificate(it.certificate.toCertificates())

                        log.debug("Up-serting server: {}", it.certificate.commonName)
                        database.functionsQueries.upsertVoiceServers(it.toVoiceServers())
                    }

                    database.functionsQueries.deleteObsoleteNeededServers(servers.map { it.certificate.commonName })
                    database.functionsQueries.deleteObsoleteCertificates()
                }

                call.respond(HttpStatusCode.Accepted)
            }
        }
    }
}

fun Certificate.toCertificates() = Certificates(
    id,
    issuerName,
    commonName,
    notBefore.toJavaLocalDateTime(),
    notAfter.toJavaLocalDateTime()
)

fun CertificateWithIPData.toVoiceServers() = VoiceServers(
    certificate.commonName,
    ipInfo.ip,
    ipInfo.city,
    ipInfo.region,
    ipInfo.country,
    ipInfo.loc,
    ipInfo.org,
    ipInfo.postal,
    ipInfo.timezone,
    certificate.id
)
