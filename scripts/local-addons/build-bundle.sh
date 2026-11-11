#!/usr/bin/env bash
# fork:local-addons - builds the bundle Nuvio runs in the background: Node 24, Redis 8 (Windows),
# AIOStreams and AIOMetadata, each pinned below. CI runs it on the Windows runner (Git Bash) and
# copies the result to <app>\resources\local-addons; Nuvio's LocalAddonServers starts it from there.
#
#   scripts/local-addons/build-bundle.sh <out-dir> [work-dir]
#
# Needs node 24 + npm on PATH (the native modules are built for the machine that runs this), git,
# curl and unzip (or PowerShell). On Windows run it with `bash -o igncr` if the checkout has CRLF endings.
# NUVIO_LOCAL_ADDONS_PLATFORM=linux builds a Linux bundle for testing (no Node or
# Redis download: it copies the node on PATH and skips Redis).
#
# Bumping a version: change a pin, push; CI rebuilds the bundle (its cache key is this file's hash)
# and the in-app updater downloads only the files that changed.
set -euo pipefail

NODE_VERSION=24.22.2
REDIS_VERSION=8.2.9
REDIS_SHA256=dcff676e861a4ae0a9854556239398e77a7469c9379af64a4a76798d166d1aa0
AIOSTREAMS_REPO=https://github.com/Viren070/AIOStreams.git
AIOSTREAMS_COMMIT=1d680ef9dd02696c21b4303e106be9707e4188aa   # v2.37.0, AGPL-3.0-only
AIOMETADATA_REPO=https://github.com/cedya77/aiometadata.git
AIOMETADATA_COMMIT=d56b78c4856620b7be8791f13d7ec1c0ddac721c  # v3.8.3, Apache-2.0
PNPM_VERSION=11.0.8

OUT=${1:?usage: build-bundle.sh <out-dir> [work-dir]}
WORK=${2:-$(mktemp -d)}
PLATFORM=${NUVIO_LOCAL_ADDONS_PLATFORM:-windows}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT" "$WORK"
OUT=$(cd "$OUT" && pwd)
WORK=$(cd "$WORK" && pwd)
log() { echo "[local-addons] $*"; }

retry() { local n=0; until "$@"; do n=$((n + 1)); [ $n -ge 4 ] && return 1; log "retry $n: $*"; sleep $((n * 5)); done; }

# unzip, or PowerShell's Expand-Archive where unzip is missing (a plain MSYS2 install, used by D:\NuvioBuild\build.cmd).
extract_zip() { # zip dir
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$1" -d "$2"
    else
        mkdir -p "$2"
        powershell.exe -NoProfile -Command "Expand-Archive -LiteralPath '$(cygpath -w "$1")' -DestinationPath '$(cygpath -w "$2")' -Force"
    fi
}

clone_at() { # repo commit dir
    rm -rf "$3"
    git init -q "$3"
    git -C "$3" remote add origin "$1"
    retry git -C "$3" fetch -q --depth 1 origin "$2"
    git -C "$3" checkout -q FETCH_HEAD
}

trace() { # app-dir out-dir entry-dir...
    if [ ! -d "$WORK/nft/node_modules/@vercel/nft" ]; then
        mkdir -p "$WORK/nft"
        ( cd "$WORK/nft" && retry npm i --no-audit --no-fund --no-save @vercel/nft@0.30.4 )
    fi
    cp "$HERE/trace-copy.mjs" "$WORK/nft/trace-copy.mjs"
    node "$WORK/nft/trace-copy.mjs" "$@"
}

rm -rf "$OUT"/{node,redis,aiostreams,aiometadata,licenses}
mkdir -p "$OUT/licenses"

# --- Node ---------------------------------------------------------------------------------------
if [ "$PLATFORM" = windows ]; then
    log "Node $NODE_VERSION"
    retry curl -fsSL -o "$WORK/node.zip" "https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-win-x64.zip"
    retry curl -fsSL -o "$WORK/SHASUMS256.txt" "https://nodejs.org/dist/v$NODE_VERSION/SHASUMS256.txt"
    want=$(grep " node-v$NODE_VERSION-win-x64.zip\$" "$WORK/SHASUMS256.txt" | cut -d' ' -f1)
    got=$(sha256sum "$WORK/node.zip" | cut -d' ' -f1)
    [ "$want" = "$got" ] || { echo "node zip checksum mismatch" >&2; exit 1; }
    rm -rf "$WORK/node-x"; extract_zip "$WORK/node.zip" "$WORK/node-x"
    mkdir -p "$OUT/node"
    cp "$WORK/node-x/node-v$NODE_VERSION-win-x64/node.exe" "$OUT/node/"
    cp "$WORK/node-x/node-v$NODE_VERSION-win-x64/LICENSE" "$OUT/licenses/node-LICENSE.txt"
else
    mkdir -p "$OUT/node"; cp "$(command -v node)" "$OUT/node/node"
fi
case "$(node -v)" in v24.*) ;; *) echo "node 24 must be on PATH to build the native modules (found $(node -v))" >&2; exit 1;; esac

# --- Redis 8 (AIOMetadata needs HSETEX/HTTL, Redis 8.0+) -----------------------------------------
if [ "$PLATFORM" = windows ]; then
    log "Redis $REDIS_VERSION"
    retry curl -fsSL -o "$WORK/redis.zip" \
        "https://github.com/redis-windows/redis-windows/releases/download/$REDIS_VERSION/Redis-$REDIS_VERSION-Windows-x64-msys2.zip"
    [ "$(sha256sum "$WORK/redis.zip" | cut -d' ' -f1)" = "$REDIS_SHA256" ] || { echo "redis zip checksum mismatch" >&2; exit 1; }
    rm -rf "$WORK/redis-x"; extract_zip "$WORK/redis.zip" "$WORK/redis-x"
    src="$WORK/redis-x/Redis-$REDIS_VERSION-Windows-x64-msys2"
    mkdir -p "$OUT/redis"
    cp "$src"/redis-server.exe "$src"/redis-cli.exe "$src"/msys-*.dll "$OUT/redis/"
    cat > "$OUT/licenses/redis-NOTICE.txt" <<EOF
Redis $REDIS_VERSION (redis.io), used under the GNU Affero General Public License v3 (one of the
licences Redis 8 is offered under). Windows build: https://github.com/redis-windows/redis-windows
release $REDIS_VERSION (MSYS2). Source: https://github.com/redis/redis/tree/$REDIS_VERSION
EOF
fi

# --- AIOStreams (Viren070, AGPL-3.0-only) ---------------------------------------------------------
log "AIOStreams $AIOSTREAMS_COMMIT"
A="$WORK/aiostreams"
clone_at "$AIOSTREAMS_REPO" "$AIOSTREAMS_COMMIT" "$A"
if [ -n "${NUVIO_TORBOX_SDK_DIR:-}" ]; then
    # Only for sandboxes that cannot download GitHub tarballs (codeload); CI never sets this.
    sed -i "s#\"github:viren070/torbox-sdk-js\\#dist\"#\"file:$NUVIO_TORBOX_SDK_DIR\"#" "$A/packages/core/package.json"
    frozen=--no-frozen-lockfile
else
    frozen=--frozen-lockfile
fi
( cd "$A"
  export npm_config_store_dir="$WORK/pnpm-store"
  retry npx -y "pnpm@$PNPM_VERSION" install $frozen
  npx -y "pnpm@$PNPM_VERSION" run build
  # resources/metadata.json (version shown on its pages). The clone is shallow with no tags, so the
  # version and commit are passed in rather than read from git.
  node scripts/generateMetadata.cjs --channel=dev "--ref=v$(node -p "require('./package.json').version")" "--commit=$AIOSTREAMS_COMMIT"
  # Production dependencies only, flat (no symlink farm) so the folder copies and zips cleanly.
  rm -rf node_modules packages/*/node_modules
  retry npx -y "pnpm@$PNPM_VERSION" install --prod $frozen --config.node-linker=hoisted
)
D="$OUT/aiostreams"
# Only the files the server can load (see trace-copy.mjs): 65 MB instead of about 800 MB.
trace "$A" "$D" packages/server/dist packages/core/dist packages/crypto
# Direct dependencies go in whole (about 55 MB, mostly traced already): some are found by name at run
# time (`require('<pkg>/package.json')`), which a trace cannot see.
for dep in $(node -e '
  const fs = require("fs");
  const names = new Set();
  for (const p of process.argv.slice(1)) Object.keys(JSON.parse(fs.readFileSync(p)).dependencies || {}).forEach((d) => names.add(d));
  console.log([...names].filter((d) => !d.startsWith("@aiostreams/core") && !d.startsWith("@aiostreams/crypto")).join(" "));
' "$A/packages/server/package.json" "$A/packages/core/package.json"); do
    if [ -e "$A/node_modules/$dep" ]; then
        rm -rf "$D/node_modules/$dep"
        mkdir -p "$(dirname "$D/node_modules/$dep")"
        cp -RL "$A/node_modules/$dep" "$D/node_modules/$dep"
    fi
done
rm -rf "$D/packages/crypto/node_modules" "$D/packages/crypto/build/Release/obj" "$D/packages/crypto/build/Release/obj.target"
mkdir -p "$D/packages/frontend" "$D/packages/web"
cp "$A"/package.json "$A"/LICENSE "$D/"
cp "$A"/packages/core/package.json "$D/packages/core/"
cp "$A"/packages/server/package.json "$D/packages/server/"
cp -R "$A"/packages/frontend/dist "$D/packages/frontend/"
cp -R "$A"/packages/web/dist "$D/packages/web/"
cp -R "$A"/packages/server/src/static "$D/packages/server/dist/static"
cp -R "$A"/resources "$A"/scripts "$D/"
# pnpm links the workspace packages (@aiostreams/core for the server, @aiostreams/crypto for core);
# links do not survive a zip, so small redirect packages stand in for them.
mkdir -p "$D/packages/server/node_modules/@aiostreams/core" "$D/packages/core/node_modules/@aiostreams/crypto"
printf '{"name":"@aiostreams/core","type":"module","main":"index.js"}\n' > "$D/packages/server/node_modules/@aiostreams/core/package.json"
printf "export * from '../../../../core/dist/index.js';\n" > "$D/packages/server/node_modules/@aiostreams/core/index.js"
printf '{"name":"@aiostreams/crypto","main":"index.js"}\n' > "$D/packages/core/node_modules/@aiostreams/crypto/package.json"
printf "module.exports = require('../../../../crypto/index.js');\n" > "$D/packages/core/node_modules/@aiostreams/crypto/index.js"
cp "$A"/LICENSE "$OUT/licenses/aiostreams-LICENSE.txt"

# --- AIOMetadata (cedya77, Apache-2.0) ----------------------------------------------------------
log "AIOMetadata $AIOMETADATA_COMMIT"
M="$WORK/aiometadata"
clone_at "$AIOMETADATA_REPO" "$AIOMETADATA_COMMIT" "$M"
( cd "$M"
  retry npm ci --no-audit --no-fund
  npm run build
  npm run build:backend
  rm -rf node_modules
  retry npm ci --omit=dev --no-audit --no-fund
)
D="$OUT/aiometadata"
trace "$M" "$D" dist/server addon
cp "$M"/package.json "$M"/LICENSE "$D/"
cp -R "$M"/dist/client "$D/dist/"
cp -R "$M"/public "$D/"
cp "$M"/LICENSE "$OUT/licenses/aiometadata-LICENSE.txt"

# --- Nuvio's own pieces -------------------------------------------------------------------------
cp "$HERE/loopback.cjs" "$OUT/loopback.cjs"
cat > "$OUT/SOURCES.txt" <<EOF
The background servers in this folder are unmodified builds of:
  AIOStreams   $AIOSTREAMS_REPO  commit $AIOSTREAMS_COMMIT  (AGPL-3.0-only)
  AIOMetadata  $AIOMETADATA_REPO  commit $AIOMETADATA_COMMIT  (Apache-2.0)
  Redis        https://github.com/redis/redis  $REDIS_VERSION  (AGPL-3.0; Windows build by redis-windows)
  Node.js      https://nodejs.org  v$NODE_VERSION  (MIT)
Nuvio runs them as separate programs on this computer only (127.0.0.1). The complete source of each
is at the address above; licence texts are in the licenses folder.
EOF
cat > "$OUT/versions.json" <<EOF
{"node":"$NODE_VERSION","redis":"$REDIS_VERSION","aiostreams":"$AIOSTREAMS_COMMIT","aiometadata":"$AIOMETADATA_COMMIT"}
EOF
log "bundle ready in $OUT ($(du -sh "$OUT" | cut -f1))"
