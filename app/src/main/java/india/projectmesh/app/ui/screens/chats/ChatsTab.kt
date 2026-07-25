package india.projectmesh.app.ui.screens.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Entry point for the Chats tab (DESIGN-BRIEF.md §9 screens 6-17), the tab the brief says gets
 * "the most design attention and the most polish of anything in the app." */
@Composable
fun ChatsTab() {
    var route by remember { mutableStateOf<ChatsRoute>(ChatsRoute.List) }

    when (val current = route) {
        is ChatsRoute.List -> ConversationListScreen(
            onOpenDirect = { contact -> route = ChatsRoute.DirectThread(contact.fingerprintHex) },
            onOpenNewConversation = { route = ChatsRoute.NewConversationSheet },
        )
        is ChatsRoute.NewConversationSheet -> {
            ConversationListScreen(
                onOpenDirect = { contact -> route = ChatsRoute.DirectThread(contact.fingerprintHex) },
                onOpenNewConversation = {},
            )
            NewConversationSheet(
                onDismiss = { route = ChatsRoute.List },
                onScanCode = { route = ChatsRoute.ScanCode },
                onJoinChannelByName = { route = ChatsRoute.JoinChannelByName },
                onCreateGroup = { route = ChatsRoute.CreateGroup },
            )
        }
        is ChatsRoute.ScanCode -> ScanCodeScreen(
            onManualAdd = { contact -> route = ChatsRoute.VerifyInPerson(contact.fingerprintHex) },
            onBack = { route = ChatsRoute.List },
        )
        is ChatsRoute.VerifyInPerson -> VerifyInPersonScreen(
            fingerprintHex = current.fingerprintHex,
            onConfirmed = { route = ChatsRoute.DirectThread(current.fingerprintHex) },
            onNotNow = { route = ChatsRoute.DirectThread(current.fingerprintHex) },
        )
        is ChatsRoute.DirectThread -> DirectConversationScreen(
            fingerprintHex = current.fingerprintHex,
            onBack = { route = ChatsRoute.List },
        )
        is ChatsRoute.JoinChannelByName -> JoinChannelScreen(
            onJoined = { session -> route = ChatsRoute.ChannelThread(session.selectorHex) },
            onBack = { route = ChatsRoute.List },
        )
        is ChatsRoute.ChannelThread -> ChannelConversationScreen(
            selectorHex = current.selectorHex,
            onBack = { route = ChatsRoute.List },
        )
        is ChatsRoute.CreateGroup -> CreateGroupScreen(
            onGroupCreated = { session -> route = ChatsRoute.GroupThread(session.selectorHex) },
            onBack = { route = ChatsRoute.List },
        )
        is ChatsRoute.GroupThread -> GroupConversationScreen(
            selectorHex = current.selectorHex,
            onBack = { route = ChatsRoute.List },
        )
    }
}
