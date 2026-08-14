#!/usr/bin/env python3
"""Fails if scry-no-op does not expose everything the real modules do.

The no-op artifact is only safe if swapping it in cannot break compilation. That
guarantee decays the moment someone adds a public method to a real module and
forgets the stub — which is exactly the kind of omission code review misses.

Compares public/protected members of every public class in the real desktop jars
against the no-op desktop jar. Extra members in the no-op are fine; missing ones
are a build failure.

Usage: check-noop-parity.py <no-op.jar> <real.jar> [real.jar ...]
"""
import re
import subprocess
import sys
import zipfile

# Compiler-generated members that are not part of the source API.
NOISE = re.compile(
    r"\b(access\$|\$default|\$annotations|componentN|"
    r"static \{\}|class file for|Compiled from)"
)

# Types the compiler emits that no caller ever names in source.
SYNTHETIC_TYPES = re.compile(
    r"ComposableSingletons\$"      # Compose lambda holders
    r"|\$Companion$"               # kotlinx.serialization companions
    r"|\$serializer$",
)

# javap reports Kotlin `internal` as JVM-public, so a signature diff alone would
# demand the no-op mirror declarations that are not part of the API at all.
# Reading @Metadata would be exact; until then these are excluded by name.
#
# Keep this list tight — anything added here stops being checked. Every entry
# must be `internal` in the real source.
INTERNAL_NAMES = {
    "ScryLock", "TestScryScope",
    "currentTimeMillis", "scryStorageDirectory", "isDebuggableBuild", "applicationId",
    "redactWith", "readTextOrNull",
    "formatBytes", "prettyPrintJson", "NetworkScreen",
    "parseAs", "PreferencesScreen", "discoverStores",
    "SharedPreferencesStore", "JavaPreferencesStore",
    "DatabaseScreen", "discoverDatabases", "updateCell", "CellEdit",
    "CrashScreen", "installCrashHandlers", "AnrWatchdog", "nowMillis",
    "LogScreen", "startSystemLogCapture", "parseLogcatLine", "attach",
    "MockScreen", "globToRegex", "TrafficTab", "mockedCall", "sleepQuietly",
    "toOkHttpResponse", "pillLabel", "pillColour",
    "PerfScreen", "FrameRing", "StartupTracker", "monotonicMillis", "processStartMillis",
    "perfPlatform", "startPerfMonitor", "roundTo", "epochMillisToIso8601",
    "BudgetBar", "WaterfallBar", "FrameSparkline", "StatCell",
}


def is_internal(signature: str) -> bool:
    return any(re.search(rf"\b{re.escape(name)}\b", signature) for name in INTERNAL_NAMES)


def public_classes(jar: str) -> list[str]:
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n.endswith(".class")]
    out = []
    for n in names:
        fqcn = n[:-len(".class")].replace("/", ".")
        # Skip synthetic nested classes (lambdas, WhenMappings, etc).
        if re.search(r"\$\d+$|\$WhenMappings|\$\$", fqcn):
            continue
        if SYNTHETIC_TYPES.search(fqcn):
            continue
        out.append(fqcn)
    return out


def signatures(jar: str, classpath: str) -> set[str]:
    classes = public_classes(jar)
    if not classes:
        return set()
    result = subprocess.run(
        ["javap", "-protected", "-cp", classpath, *classes],
        capture_output=True, text=True,
    )
    sigs, current = set(), None
    for line in result.stdout.splitlines():
        stripped = line.strip()
        if not stripped or NOISE.search(stripped):
            continue
        if stripped.endswith("{") and (" class " in stripped or " interface " in stripped):
            m = re.search(r"(?:class|interface)\s+([\w.$]+)", stripped)
            current = m.group(1) if m else None
            # File facades (`FooKt`) are not nameable types in Kotlin source —
            # only their members are callable, and those are checked below. A
            # facade whose declarations are all `internal` is not API at all.
            if current and not is_internal(current) and not current.endswith("Kt"):
                sigs.add(f"TYPE {current}")
            continue
        if current and stripped.endswith(";") and not is_internal(stripped) \
                and not is_internal(current):
            # Normalise the declaring type away so only the member shape matters.
            sigs.add(f"{current} :: {stripped}")
    return sigs


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2

    noop_jar, real_jars = sys.argv[1], sys.argv[2:]
    classpath = ":".join([noop_jar, *real_jars])

    noop = signatures(noop_jar, classpath)
    missing: dict[str, list[str]] = {}

    for jar in real_jars:
        for sig in signatures(jar, classpath):
            if sig not in noop:
                missing.setdefault(jar.split("/")[-1], []).append(sig)

    if not missing:
        print(f"no-op parity OK — {len(noop)} signatures cover "
              f"{len(real_jars)} real module(s)")
        return 0

    total = sum(len(v) for v in missing.values())
    print(f"no-op parity FAILED — {total} public member(s) missing from scry-no-op:\n")
    for jar, sigs in sorted(missing.items()):
        print(f"  from {jar}:")
        for sig in sorted(sigs):
            print(f"    {sig}")
        print()
    print("Add the missing declarations to scry-no-op so the release swap stays source-compatible.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
