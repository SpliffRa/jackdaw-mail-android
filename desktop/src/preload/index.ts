import { contextBridge, ipcRenderer } from 'electron'
import { electronAPI } from '@electron-toolkit/preload'
import {
  nativeMenuActionChannel,
  nativeMenuLabelsChannel,
  type NativeMenuLabels,
} from '../../../app/logic/util/nativeMenu'

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
