package app.jackdaw.client.ui.navigation

sealed class Screen(val route: String) {
    data object MailList : Screen("mail_list")
    data object MailDetail : Screen("mail_detail?emailId={emailId}") {
        fun createRoute(emailId: String): String {
            val encoded = java.net.URLEncoder.encode(emailId, "UTF-8")
            return "mail_detail?emailId=$encoded"
        }
    }
    data object Compose : Screen("compose?replyToId={replyToId}") {
        fun createRoute(replyToId: String? = null): String {
            return if (replyToId != null) {
                "compose?replyToId=" + java.net.URLEncoder.encode(replyToId, "UTF-8")
            } else {
                "compose"
            }
        }
    }
    data object Settings : Screen("settings")
    data object Calendar : Screen("calendar")
}
