import UIKit
import WebKit

/// The whole shell: one view controller hosting one web view that plays the
/// injected payload.
final class ShellViewController: UIViewController {

    private var webView: WKWebView!
    private let config: ShellConfig

    init(config: ShellConfig) {
        self.config = config
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not used; the shell builds its view in code")
    }

    override func loadView() {
        let configuration = WKWebViewConfiguration()

        let root = Bundle.main.bundleURL.appendingPathComponent(WwwSchemeHandler.wwwRoot)
        configuration.setURLSchemeHandler(
            WwwSchemeHandler(rootDirectory: root),
            forURLScheme: WwwSchemeHandler.scheme
        )

        // Let the game start its own music and keep video in the page — without
        // these an opening track never sounds and every video is yanked into
        // the system's fullscreen player.
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsInlineMediaPlayback = true
        configuration.allowsPictureInPictureMediaPlayback = false
        configuration.suppressesIncrementalRendering = false

        webView = WKWebView(frame: .zero, configuration: configuration)
        webView.isOpaque = true
        webView.backgroundColor = config.backgroundColor
        webView.scrollView.backgroundColor = config.backgroundColor
        // A visual novel is a fixed surface: no bounce, no zoom, no scrollbars.
        webView.scrollView.bounces = false
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.showsHorizontalScrollIndicator = false
        webView.scrollView.showsVerticalScrollIndicator = false
        webView.allowsBackForwardNavigationGestures = false
        webView.allowsLinkPreview = false

        #if DEBUG
        // Release builds never enable this: it would let anything attached to
        // the device inspect the game's web view.
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        #endif

        view = webView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = config.backgroundColor
        webView.load(URLRequest(url: WwwSchemeHandler.entryURL))
    }

    // The game paints every pixel, cutout included.
    override var prefersStatusBarHidden: Bool { true }
    override var prefersHomeIndicatorAutoHidden: Bool { true }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { .all }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { config.orientation.mask }
}
