import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'
import {
  nativeMenuActionChannel,
  nativeMenuLabelsChannel,
  type NativeMenuLabels,
} from '../../../app/logic/util/nativeMenu'
import {
  composeWindowCloseChannel,
  composeWindowClosedChannel,
  composeWindowDataChannel,
  composeWindowFocusChannel,
  composeWindowOpenChannel,
  type ComposeWindowMail,
} from '../../../app/logic/Mail/Composer/ComposeWindowProtocol'

// Custom APIs for renderer
const api = {
  onNativeMenuAction: (callback: (action: string) => void): (() => void) => {
    const listener = (_event: Electron.IpcRendererEvent, action: unknown): void => {
      if (typeof action === 'string') {
        callback(action)
      }
    }
    ipcRenderer.on(nativeMenuActionChannel, listener)
    return () => ipcRenderer.removeListener(nativeMenuActionChannel, listener)
  },
  setNativeMenuLabels: (labels: NativeMenuLabels): void => {
    ipcRenderer.send(nativeMenuLabelsChannel, labels)
  },
  openComposeWindow: (windowID: string, payload: ComposeWindowMail): void => {
    ipcRenderer.send(composeWindowOpenChannel, windowID, payload)
  },
  focusComposeWindow: (windowID: string): void => {
    ipcRenderer.send(composeWindowFocusChannel, windowID)
  },
  closeComposeWindow: (windowID: string): void => {
    ipcRenderer.send(composeWindowCloseChannel, windowID)
  },
  getComposeWindowData: (windowID: string): Promise<ComposeWindowMail | null> => {
    return ipcRenderer.invoke(composeWindowDataChannel, windowID)
  },
  onComposeWindowClosed: (callback: (windowID: string) => void): (() => void) => {
    const listener = (_event: Electron.IpcRendererEvent, windowID: unknown): void => {
      if (typeof windowID === 'string') {
        callback(windowID)
      }
    }
    ipcRenderer.on(composeWindowClosedChannel, listener)
    return () => ipcRenderer.removeListener(composeWindowClosedChannel, listener)
  }
}

// Use `contextBridge` APIs to expose Electron APIs to
// renderer only if context isolation is enabled, otherwise
// just add to the DOM global.
if (process.contextIsolated) {
  try {
    contextBridge.exposeInMainWorld('electron', electronAPI)
    contextBridge.exposeInMainWorld('api', api)
  } catch (error) {
    console.error(error)
  }
} else {
  // @ts-ignore (define in dts)
  window.electron = electronAPI
  // @ts-ignore (define in dts)
  window.api = api
}
