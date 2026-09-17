import { ElectronAPI } from '@electron-toolkit/preload'
import type { NativeMenuLabels } from '../../../app/logic/util/nativeMenu'
import type {
  ComposeWindowMail,
  ComposeWindowPerson,
  ComposeWindowSendResult,
} from '../../../app/logic/Mail/Composer/ComposeWindowProtocol'

declare global {
  interface Window {
    electron: ElectronAPI
    api: {
      onNativeMenuAction(callback: (action: string) => void): () => void
      setNativeMenuLabels(labels: NativeMenuLabels): void
      openComposeWindow(windowID: string, payload: ComposeWindowMail): void
      focusComposeWindow(windowID: string): void
      closeComposeWindow(windowID: string): void
      getComposeWindowData(windowID: string): Promise<ComposeWindowMail | null>
      sendComposeWindowMail(windowID: string, payload: ComposeWindowMail): Promise<ComposeWindowSendResult>
      searchComposeWindowContacts(windowID: string, searchText: string): Promise<ComposeWindowPerson[]>
      onComposeWindowSendRequest(callback: (requestID: string, windowID: string, payload: ComposeWindowMail) => void): () => void
      respondToComposeWindowSend(requestID: string, result: ComposeWindowSendResult): void
      onComposeWindowSearchContactsRequest(callback: (requestID: string, windowID: string, searchText: string) => void): () => void
      respondToComposeWindowSearchContacts(requestID: string, result: ComposeWindowPerson[]): void
      onComposeWindowClosed(callback: (windowID: string) => void): () => void
    }
  }
}
