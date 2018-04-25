import com.squareup.moshi.Moshi
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.async
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*
import kotlin.properties.Delegates

fun main(args: Array<String>) {

    embeddedServer(Netty, 3000) {
        var conn: Connection by Delegates.notNull()
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost/iotcoffee?user=root&password=root&useSSL=false&serverTimezone=America/New_York")
            // let's go fellas

        } catch (ex: SQLException) {
            // handle any errors
            println("Messages: ${ex.message}")
            System.out.println("SQLState: " + ex.sqlState);
            System.out.println("VendorError: ${ex.errorCode}");
            throw RuntimeException("We are failures")
        }

        val moshi = Moshi.Builder().build()
        val msgAdapter = moshi.adapter<ResponseMsg>(ResponseMsg::class.java)

        val stmt = conn.createStatement()
        var retrofit: Retrofit by Delegates.notNull()

        routing {
            post("/register") {
                val params = call.receiveParameters()
                val email: String = params["email"] as String
                val password: String = params["password"] as String
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE email='$email';")
                rs.next()
                if (rs.getInt(1) > 0) {
                    call.respond(HttpStatusCode.Unauthorized, msgAdapter.toJson(ResponseMsg("User already exists")))
                    return@post
                } else {
                    stmt.executeUpdate("INSERT INTO users (email, password) VALUES ('$email', '$password');")
                    call.respond(msgAdapter.toJson(ResponseMsg("Successfully registered!")))
                    return@post
                }
            }
            post("/login") {
                val params = call.receiveParameters()
                val email: String = params["email"] as String
                val password: String = params["password"] as String

                val rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE email='$email' AND password='$password';")
                rs.next()
                if (rs.getInt(1) > 0) {
                    val uuid = UUID.randomUUID()
                    stmt.executeUpdate("INSERT INTO logins (email, token) VALUES ('$email', '$uuid')")
                    call.respond(msgAdapter.toJson(ResponseMsg(uuid.toString())))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, msgAdapter.toJson(ResponseMsg("Invalid username or password")))
                }
            }

            post("/setBrew") {
                val params = call.receiveParameters()
            }

            post("/brew") {
                val params = call.receiveParameters()
                val email: String = params["email"] as String
                val rs = stmt.executeQuery("SELECT url FROM cmaker WHERE email='$email';")
                rs.next()
                val url = rs.getString("url")
                if (url.isEmpty()) {
                    call.respond(HttpStatusCode.NotAcceptable, msgAdapter.toJson(ResponseMsg("Need to set a URL to brew at!")))
                    return@post
                }

                retrofit = Retrofit.Builder()
                        .baseUrl(url)
                        .addConverterFactory(MoshiConverterFactory.create())
                        .build()

                val brew = retrofit.create(BrewModel::class.java)
                brew.brewCoffee().enqueue(object : Callback<Msg> {
                    override fun onFailure(ret: Call<Msg>, t: Throwable) {
                        async {
                            call.respond(HttpStatusCode.NotAcceptable, msgAdapter.toJson(ResponseMsg("Unable to reach coffee maker")))
                        }
                        return
                    }

                    override fun onResponse(ret: Call<Msg>, response: Response<Msg>) {
                        async {
                            if (response.isSuccessful) {
                                call.respond(msgAdapter.toJson(ResponseMsg("Successfully started brewing coffee!")))
                            } else {
                                call.respond(msgAdapter.toJson(ResponseMsg("Error brewing coffee")))
                            }
                        }
                    }

                })

            }
        }
        println("Internet coffee server started")
    }.start(wait = true)
}

data class ResponseMsg(val msg: String)