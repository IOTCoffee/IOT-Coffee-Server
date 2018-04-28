import retrofit2.Call
import retrofit2.http.GET

interface BrewModel {
    @GET("/")
    fun brewCoffee() : Call<Msg>

    @GET("/ping")
    fun brewPing() : Call<Msg>

}

data class Msg(val msg: String)
