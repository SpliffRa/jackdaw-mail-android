import { ElectronAPI } from '@electron-toolkit/preload'
import type { NativeMenuLabels } from '../../../app/logic/util/nativeMenu'
import type { ComposeWindowMail } from '../../../app/logic/Mail/Composer/ComposeWindowProtocol'

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
      onComposeWindowClosed(callback: (windowID: string) => void): () => void
    }
  }
}
