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
  composeWindowSendChannel,
  composeWindowSendRequestChannel,
  composeWindowSendResponseChannel,
  composeWindowSearchContactsChannel,
  composeWindowSearchContactsRequestChannel,
  composeWindowSearchContactsResponseChannel,
  isComposeWindowPayload,
  isComposeWindowSendResult,
  isComposeWindowPersonArray,
  type ComposeWindowMail,
  type ComposeWindowPerson,
  type ComposeWindowSendResult,
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
  sendComposeWindowMail: async (windowID: string, payload: ComposeWindowMail): Promise<ComposeWindowSendResult> => {
    let result: unknown = await ipcRenderer.invoke(composeWindowSendChannel, windowID, payload)
    return isComposeWindowSendResult(result)
      ? result
      : { ok: false, errorMessage: 'Compose window send failed' }
  },
  searchComposeWindowContacts: async (windowID: string, searchText: string): Promise<ComposeWindowPerson[]> => {
    let result: unknown = await ipcRenderer.invoke(composeWindowSearchContactsChannel, windowID, searchText)
    return isComposeWindowPersonArray(result) ? result : []
  },
  onComposeWindowSendRequest: (callback: (requestID: string, windowID: string, payload: ComposeWindowMail) => void): (() => void) => {
    const listener = (
      _event: Electron.IpcRendererEvent,
      requestID: unknown,
      windowID: unknown,
      payload: unknown,
    ): void => {
      if (typeof requestID === 'string' && typeof windowID === 'string' && isComposeWindowPayload(payload)) {
        callback(requestID, windowID, payload)
      }
    }
    ipcRenderer.on(composeWindowSendRequestChannel, listener)
    return () => ipcRenderer.removeListener(composeWindowSendRequestChannel, listener)
  },
  respondToComposeWindowSend: (requestID: string, result: ComposeWindowSendResult): void => {
    ipcRenderer.send(composeWindowSendResponseChannel, requestID, result)
  },
  onComposeWindowSearchContactsRequest: (callback: (requestID: string, windowID: string, searchText: string) => void): (() => void) => {
    const listener = (
      _event: Electron.IpcRendererEvent,
      requestID: unknown,
      windowID: unknown,
      searchText: unknown,
    ): void => {
      if (typeof requestID === 'string' && typeof windowID === 'string' &&
          typeof searchText === 'string') {
        callback(requestID, windowID, searchText)
      }
    }
    ipcRenderer.on(composeWindowSearchContactsRequestChannel, listener)
    return () => ipcRenderer.removeListener(composeWindowSearchContactsRequestChannel, listener)
  },
  respondToComposeWindowSearchContacts: (requestID: string, result: ComposeWindowPerson[]): void => {
    ipcRenderer.send(composeWindowSearchContactsResponseChannel, requestID, result)
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
