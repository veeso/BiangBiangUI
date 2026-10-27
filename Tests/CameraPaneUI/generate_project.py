"""Generate the native pane UI-test project outside the checkout.

Usage: python3 Tests/CameraPaneUI/generate_project.py /tmp/pane-project
Then: xcodebuild test -project /tmp/pane-project/PaneHarness.xcodeproj \
    -scheme PaneHarness -collect-test-diagnostics never \
    -destination 'platform=iOS Simulator,id=...'

Uses only Python's standard library. The package remains the source of the
production pane; the app fixture makes its internal UI available to XCUITest.
"""

import json
import sys
from pathlib import Path

fixture = Path(__file__).resolve().parent
repository = fixture.parent.parent
root = Path(sys.argv[1]).resolve() / "PaneHarness.xcodeproj"
if root.is_relative_to(repository):
    raise SystemExit("Generate the test project in a system temporary directory")
(root / "xcshareddata/xcschemes").mkdir(parents=True, exist_ok=True)
objects = {}


def obj(isa, **props):
    key = f"{len(objects) + 1:024X}"
    objects[key] = dict(isa=isa, **props)
    return key


app_file = obj(
    "PBXFileReference",
    lastKnownFileType="sourcecode.swift",
    path=str(fixture / "PaneHost.swift"),
    sourceTree="<absolute>",
)
test_file = obj(
    "PBXFileReference",
    lastKnownFileType="sourcecode.swift",
    path=str(fixture / "PaneUITests.swift"),
    sourceTree="<absolute>",
)
app_product = obj(
    "PBXFileReference",
    explicitFileType="wrapper.application",
    path="PaneHost.app",
    sourceTree="BUILT_PRODUCTS_DIR",
)
test_product = obj(
    "PBXFileReference",
    explicitFileType="wrapper.cfbundle",
    path="PaneUITests.xctest",
    sourceTree="BUILT_PRODUCTS_DIR",
)
products = obj(
    "PBXGroup",
    children=[app_product, test_product],
    name="Products",
    sourceTree="<group>",
)
main = obj("PBXGroup", children=[app_file, test_file, products], sourceTree="<group>")
package = obj("XCLocalSwiftPackageReference", relativePath=str(repository))
lib = obj(
    "XCSwiftPackageProductDependency", package=package, productName="BiangBiangUI"
)
libbuild = obj("PBXBuildFile", productRef=lib)


def phase(file):
    build = obj("PBXBuildFile", fileRef=file)
    return obj(
        "PBXSourcesBuildPhase",
        buildActionMask=2147483647,
        files=[build],
        runOnlyForDeploymentPostprocessing=0,
    )


def configs(settings):
    return obj(
        "XCConfigurationList",
        buildConfigurations=[
            obj("XCBuildConfiguration", buildSettings=settings, name="Debug")
        ],
        defaultConfigurationIsVisible=0,
        defaultConfigurationName="Debug",
    )


common = {
    "ALWAYS_SEARCH_USER_PATHS": "NO",
    "ONLY_ACTIVE_ARCH": "YES",
    "SWIFT_VERSION": "6.0",
    "IPHONEOS_DEPLOYMENT_TARGET": "17.0",
    "SDKROOT": "iphoneos",
    "SUPPORTED_PLATFORMS": "iphonesimulator iphoneos",
    "CODE_SIGNING_ALLOWED": "NO",
    "GENERATE_INFOPLIST_FILE": "YES",
    "TARGETED_DEVICE_FAMILY": "1,2",
    "ENABLE_TESTABILITY": "YES",
    "SWIFT_OPTIMIZATION_LEVEL": "-Onone",
}
app = obj(
    "PBXNativeTarget",
    name="PaneHost",
    productName="PaneHost",
    productReference=app_product,
    productType="com.apple.product-type.application",
    buildConfigurationList=configs(
        dict(
            common,
            PRODUCT_BUNDLE_IDENTIFIER="dev.veeso.PaneHost",
            PRODUCT_NAME="$(TARGET_NAME)",
            INFOPLIST_KEY_UIApplicationSceneManifest_Generation="YES",
            INFOPLIST_KEY_UILaunchScreen_Generation="YES",
            INFOPLIST_KEY_UISupportedInterfaceOrientations=(
                "UIInterfaceOrientationPortrait "
                "UIInterfaceOrientationPortraitUpsideDown "
                "UIInterfaceOrientationLandscapeLeft "
                "UIInterfaceOrientationLandscapeRight"
            ),
            INFOPLIST_KEY_NSCameraUsageDescription="Test native camera preview geometry.",
        )
    ),
    buildPhases=[
        phase(app_file),
        obj(
            "PBXFrameworksBuildPhase",
            buildActionMask=2147483647,
            files=[libbuild],
            runOnlyForDeploymentPostprocessing=0,
        ),
    ],
    buildRules=[],
    dependencies=[],
    packageProductDependencies=[lib],
)
dep = obj("PBXTargetDependency", target=app)
tests = obj(
    "PBXNativeTarget",
    name="PaneUITests",
    productName="PaneUITests",
    productReference=test_product,
    productType="com.apple.product-type.bundle.ui-testing",
    buildConfigurationList=configs(
        dict(
            common,
            PRODUCT_BUNDLE_IDENTIFIER="dev.veeso.PaneUITests",
            PRODUCT_NAME="$(TARGET_NAME)",
            TEST_TARGET_NAME="PaneHost",
        )
    ),
    buildPhases=[phase(test_file)],
    buildRules=[],
    dependencies=[dep],
)
project = obj(
    "PBXProject",
    attributes={"LastUpgradeCheck": "2600"},
    buildConfigurationList=configs(common),
    compatibilityVersion="Xcode 14.0",
    developmentRegion="en",
    knownRegions=["en", "Base"],
    mainGroup=main,
    productRefGroup=products,
    projectDirPath="",
    projectRoot="",
    targets=[app, tests],
    packageReferences=[package],
)


def encode(x):
    if isinstance(x, dict):
        return "{ " + " ".join(f"{k} = {encode(v)};" for k, v in x.items()) + " }"
    if isinstance(x, list):
        return "( " + ", ".join(encode(v) for v in x) + " )"
    return json.dumps(str(x))


(root / "project.pbxproj").write_text(
    "// !$*UTF8*$!\n"
    + encode(
        {
            "archiveVersion": 1,
            "classes": {},
            "objectVersion": 56,
            "objects": objects,
            "rootObject": project,
        }
    )
    + "\n"
)
(
    root / "xcshareddata/xcschemes/PaneHarness.xcscheme"
).write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2600" version="1.3">
  <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES">
    <BuildActionEntries>
      <BuildActionEntry buildForTesting="YES" buildForRunning="YES"
                       buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">
        <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{app}"
                            BuildableName="PaneHost.app" BlueprintName="PaneHost"
                            ReferencedContainer="container:PaneHarness.xcodeproj"/>
      </BuildActionEntry>
    </BuildActionEntries>
  </BuildAction>
  <TestAction buildConfiguration="Debug"
              selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB"
              selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB"
              shouldUseLaunchSchemeArgsEnv="YES">
    <Testables>
      <TestableReference skipped="NO">
        <BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{tests}"
                            BuildableName="PaneUITests.xctest" BlueprintName="PaneUITests"
                            ReferencedContainer="container:PaneHarness.xcodeproj"/>
      </TestableReference>
    </Testables>
  </TestAction>
</Scheme>''')
