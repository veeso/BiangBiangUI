import AVFoundation

protocol CameraRotationConnection: AnyObject {
    var videoRotationAngle: CGFloat { get set }
    func isVideoRotationAngleSupported(_ angle: CGFloat) -> Bool
}

extension AVCaptureConnection: CameraRotationConnection {}
