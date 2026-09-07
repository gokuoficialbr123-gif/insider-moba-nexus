import UIKit
import WebKit

final class ViewController: UIViewController, WKNavigationDelegate, WKUIDelegate, WKDownloadDelegate, WKScriptMessageHandler {
    private var webView: WKWebView!
    private var downloadDestinations: [ObjectIdentifier: URL] = [:]

    private var indexURL: URL {
        guard let url = Bundle.main.url(forResource: "index", withExtension: "html") else {
            fatalError("index.html não foi incluído no bundle do NEXUS")
        }
        return url
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 7 / 255, green: 11 / 255, blue: 20 / 255, alpha: 1)

        let controller = WKUserContentController()
        controller.add(self, name: "nexusDownload")
        controller.addUserScript(WKUserScript(
            source: Self.nativeHelperScript,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true
        ))

        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.userContentController = controller
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.preferences.javaScriptCanOpenWindowsAutomatically = false
        configuration.applicationNameForUserAgent = "NEXUS-iOS/BETA-INSIDER"

        webView = WKWebView(frame: .zero, configuration: configuration)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.backgroundColor = view.backgroundColor
        webView.isOpaque = false
        view.addSubview(webView)

        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        loadLocal()
    }

    private func loadLocal(queryItems: [URLQueryItem] = []) {
        var components = URLComponents(url: indexURL, resolvingAgainstBaseURL: false)
        components?.queryItems = queryItems.isEmpty ? nil : queryItems
        let target = components?.url ?? indexURL
        webView.loadFileURL(target, allowingReadAccessTo: indexURL.deletingLastPathComponent())
    }

    private func isLocal(_ url: URL) -> Bool {
        guard url.isFileURL else { return false }
        return url.path.hasPrefix(indexURL.deletingLastPathComponent().path)
    }

    @discardableResult
    func handleExternalURL(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "nexus" else { return false }
        let type = url.host?.lowercased() ?? ""
        let value = url.pathComponents.filter { $0 != "/" }.last ?? ""
        if type == "reel", !value.isEmpty {
            loadLocal(queryItems: [
                URLQueryItem(name: "view", value: "reels"),
                URLQueryItem(name: "reel", value: value)
            ])
            return true
        }
        if type == "profile", !value.isEmpty {
            loadLocal(queryItems: [
                URLQueryItem(name: "view", value: "profile"),
                URLQueryItem(name: "profile", value: value)
            ])
            return true
        }
        loadLocal()
        return true
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor action: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = action.request.url else {
            decisionHandler(.cancel)
            return
        }
        let scheme = url.scheme?.lowercased() ?? ""
        if isLocal(url) || ["about", "blob", "data"].contains(scheme) {
            decisionHandler(.allow)
            return
        }
        if scheme == "nexus" {
            _ = handleExternalURL(url)
            decisionHandler(.cancel)
            return
        }
        if ["https", "http", "tel", "mailto"].contains(scheme) {
            UIApplication.shared.open(url)
        }
        decisionHandler(.cancel)
    }

    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for action: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        guard action.targetFrame == nil, let url = action.request.url else { return nil }
        if isLocal(url) {
            webView.load(URLRequest(url: url))
        } else if ["https", "http", "tel", "mailto"].contains(url.scheme?.lowercased() ?? "") {
            UIApplication.shared.open(url)
        }
        return nil
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor response: WKNavigationResponse,
        decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void
    ) {
        decisionHandler(response.canShowMIMEType ? .allow : .download)
    }

    func webView(_ webView: WKWebView, navigationAction: WKNavigationAction, didBecome download: WKDownload) {
        download.delegate = self
    }

    func webView(_ webView: WKWebView, navigationResponse: WKNavigationResponse, didBecome download: WKDownload) {
        download.delegate = self
    }

    func download(
        _ download: WKDownload,
        decideDestinationUsing response: URLResponse,
        suggestedFilename: String,
        completionHandler: @escaping (URL?) -> Void
    ) {
        let safeName = sanitizeFilename(suggestedFilename)
        let destination = FileManager.default.temporaryDirectory.appendingPathComponent(safeName)
        try? FileManager.default.removeItem(at: destination)
        downloadDestinations[ObjectIdentifier(download)] = destination
        completionHandler(destination)
    }

    func downloadDidFinish(_ download: WKDownload) {
        guard let url = downloadDestinations.removeValue(forKey: ObjectIdentifier(download)) else { return }
        presentShare(for: url)
    }

    func download(_ download: WKDownload, didFailWithError error: Error, resumeData: Data?) {
        downloadDestinations.removeValue(forKey: ObjectIdentifier(download))
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == "nexusDownload",
              let body = message.body as? [String: Any],
              let encoded = body["data"] as? String,
              let data = Data(base64Encoded: encoded) else { return }

        let rawName = (body["filename"] as? String) ?? "nexus-export"
        let mime = (body["mime"] as? String) ?? "application/octet-stream"
        let name = filenameWithExtension(sanitizeFilename(rawName), mime: mime)
        let destination = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try? FileManager.default.removeItem(at: destination)
            try data.write(to: destination, options: .atomic)
            presentShare(for: destination)
        } catch {
            showSimpleAlert("Não foi possível salvar o arquivo.")
        }
    }

    private func presentShare(for url: URL) {
        DispatchQueue.main.async {
            let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            self.present(activity, animated: true)
        }
    }

    private func sanitizeFilename(_ name: String) -> String {
        let invalid = CharacterSet(charactersIn: "\\/:*?\"<>|")
        let clean = name.components(separatedBy: invalid).joined(separator: "_")
        return clean.isEmpty ? "nexus-export" : clean
    }

    private func filenameWithExtension(_ name: String, mime: String) -> String {
        if (name as NSString).pathExtension.isEmpty {
            let ext: String
            switch mime.lowercased() {
            case "application/json": ext = "json"
            case "application/pdf": ext = "pdf"
            case "text/plain": ext = "txt"
            case "text/csv": ext = "csv"
            case "application/zip": ext = "zip"
            default: ext = "bin"
            }
            return name + "." + ext
        }
        return name
    }

    private func showSimpleAlert(_ message: String) {
        let alert = UIAlertController(title: "NEXUS", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptAlertPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping () -> Void
    ) {
        let alert = UIAlertController(title: "NEXUS", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler() })
        present(alert, animated: true)
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage message: String,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping (Bool) -> Void
    ) {
        let alert = UIAlertController(title: "NEXUS", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { _ in completionHandler(false) })
        alert.addAction(UIAlertAction(title: "Confirmar", style: .default) { _ in completionHandler(true) })
        present(alert, animated: true)
    }

    func webView(
        _ webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt prompt: String,
        defaultText: String?,
        initiatedByFrame frame: WKFrameInfo,
        completionHandler: @escaping (String?) -> Void
    ) {
        let alert = UIAlertController(title: "NEXUS", message: prompt, preferredStyle: .alert)
        alert.addTextField { $0.text = defaultText }
        alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { _ in completionHandler(nil) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completionHandler(alert.textFields?.first?.text) })
        present(alert, animated: true)
    }

    @available(iOS 15.0, *)
    func webView(
        _ webView: WKWebView,
        requestMediaCapturePermissionFor origin: WKSecurityOrigin,
        initiatedByFrame frame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: @escaping (WKPermissionDecision) -> Void
    ) {
        if origin.protocol.lowercased() == "file" || origin.host.isEmpty {
            decisionHandler(.prompt)
        } else {
            decisionHandler(.deny)
        }
    }

    deinit {
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "nexusDownload")
    }

    private static let nativeHelperScript = #"""
    (function(){
      window.NEXUS_NATIVE_APP = true;
      if (window.__nexusNativeDownloadInstalled) return;
      window.__nexusNativeDownloadInstalled = true;
      async function send(a){
        try {
          const href = a.href || '';
          const filename = a.download || 'nexus-export';
          if (href.startsWith('blob:')) {
            const blob = await fetch(href).then(r => r.blob());
            const reader = new FileReader();
            reader.onloadend = function(){
              const raw = String(reader.result || '');
              window.webkit.messageHandlers.nexusDownload.postMessage({filename, mime: blob.type || 'application/octet-stream', data: raw.split(',')[1] || ''});
            };
            reader.readAsDataURL(blob);
            return true;
          }
          if (href.startsWith('data:')) {
            const parts = href.split(',');
            const meta = parts[0];
            const mime = (meta.match(/^data:([^;,]+)/) || [])[1] || 'application/octet-stream';
            let data = parts.slice(1).join(',');
            if (!meta.includes(';base64')) data = btoa(unescape(encodeURIComponent(decodeURIComponent(data))));
            window.webkit.messageHandlers.nexusDownload.postMessage({filename, mime, data});
            return true;
          }
        } catch (e) { console.warn('native download', e); }
        return false;
      }
      document.addEventListener('click', function(e){
        const a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
        if (!a) return;
        const href = a.href || '';
        if (href.startsWith('blob:') || href.startsWith('data:')) {
          e.preventDefault();
          e.stopImmediatePropagation();
          send(a);
        }
      }, true);
    })();
    """#
}
