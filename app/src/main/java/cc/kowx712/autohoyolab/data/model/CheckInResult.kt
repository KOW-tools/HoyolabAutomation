package cc.kowx712.autohoyolab.data.model

sealed class CheckInResult(open val gameId: String) {
    data class Success(override val gameId: String) : CheckInResult(gameId)
    data class AlreadySigned(override val gameId: String) : CheckInResult(gameId)
    data class Failed(
        override val gameId: String,
        val message: String,
        val retcode: Int?,
    ) : CheckInResult(gameId)

    data class CookieExpired(override val gameId: String) : CheckInResult(gameId)
    data class NetworkError(override val gameId: String) : CheckInResult(gameId)
}
