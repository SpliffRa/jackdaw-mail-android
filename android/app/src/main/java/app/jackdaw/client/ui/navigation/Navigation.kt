package app.jackdaw.client.ui.navigation

sealed class Screen(val route: String) {
    data object MailList : Screen("mail_list")
    data object MailDetail : Screen("mail_detail/{emailId}") {
        fun createRoute(emailId: String) = "mail_detail/$emailId"
    }
    data object Compose : Screen("compose?replyToId={replyToId}") {
        fun createRoute(replyToId: String? = null) =
            if (replyToId != null) "compose?replyToId=$replyToId" else "compose"
    }
    data object Settings : Screen("settings")
}
