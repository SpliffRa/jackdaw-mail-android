import { ElectronAPI } from '@electron-toolkit/preload'
import type { NativeMenuLabels } from '../../../app/logic/util/nativeMenu'

declare global {
  interface Window {
    electron: ElectronAPI
    api: {
      onNativeMenuAction(callback: (action: string) => void): () => void
      setNativeMenuLabels(labels: NativeMenuLabels): void
    }
  }
}
