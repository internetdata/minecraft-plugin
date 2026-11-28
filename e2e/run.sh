#!/bin/bash

# Boots real servers in docker with the plugin installed, joins each one from addresses a stub API
# answers for, and checks what every login came to. On the proxies, three stand-in servers behind
# them say which one each admitted player was sent to.
#
#   ./e2e/run.sh                          # every target
#   ./e2e/run.sh paper-26.3 velocity-4.3.1
#   JAR=dist/target/InternetData-1.0.0.jar ./e2e/run.sh   # skip the build
#
# Needs docker, java 17 or newer (the probe, the stub and the stand-ins run on the host), curl, jq and
# sha256sum. Servers download into e2e/.cache, and each target's files live in e2e/.work/<target>.

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$PWD
WORK=$ROOT/e2e/.work
CACHE=$ROOT/e2e/.cache
STUB_PORT=18765
# The stand-ins behind a proxy: the one it sends everyone to, and one per continent routing names.
BACKENDS=(lobby:18801 eu:18802 na:18803)

# <target> <project> <version> <java>
TARGETS=(
    "paper-26.3 paper 26.3 25"
    "folia-26.3 folia 26.3 25"
    "paper-1.21.11 paper 1.21.11 21"
    "paper-1.20.1 paper 1.20.1 17"
    "velocity-4.3.1 velocity 4.3.1 25"
    "velocity-3.4.0 velocity 3.4.0 17"
    "bungeecord bungeecord latest 21"
)

main() {
    local wanted=("$@")
    local jar=${JAR:-}
    if [[ -z "$jar" ]]; then
        ./scripts/mvn.sh -q -DskipTests package
        jar=$(ls dist/target/InternetData-*.jar | grep -v original | head -1)
    fi
    jar=$(realpath "$jar")
    VERSION=$(unzip -p "$jar" plugin.yml | sed -n "s/^version: '\(.*\)'$/\1/p")
    mkdir -p "$WORK" "$CACHE"
    trap cleanup EXIT

    local failed=0
    local line
    for line in "${TARGETS[@]}"; do
        read -r target project version jdk <<< "$line"
        if [[ ${#wanted[@]} -gt 0 && ! " ${wanted[*]} " =~ " ${target} " ]]; then
            continue
        fi
        echo "== $target"
        if run_target "$target" "$project" "$version" "$jdk" "$jar"; then
            echo "ok   $target"
        else
            echo "FAIL $target (its files: e2e/.work/$target)"
            failed=1
        fi
        stop_target "$target"
    done
    return "$failed"
}

run_target() {
    local target=$1 project=$2 version=$3 jdk=$4 jar=$5
    local dir=$WORK/$target
    local server
    server=$(download "$project" "$version")
    rm -rf "${dir:?}"
    # Velocity names a plugin's folder by its ID, the others by its name.
    local data=$dir/plugins/InternetData
    if [[ "$project" == "velocity" ]]; then
        data=$dir/plugins/internetdata
    fi
    mkdir -p "$data"
    cp "$server" "$dir/server.jar"
    cp "$jar" "$dir/plugins/"

    local port=25565
    local proxy=n
    if [[ "$project" == "velocity" || "$project" == "bungeecord" ]]; then
        port=25577
        proxy=y
    fi
    if [[ "$proxy" == n ]]; then
        echo "eula=true" > "$dir/eula.txt"
        cat > "$dir/server.properties" <<EOF
online-mode=false
white-list=false
server-port=$port
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=2
simulation-distance=2
EOF
    fi

    # The first start writes the server's own config files, which the second start reads: online
    # mode off, the PROXY header trusted, the reconnect throttles off, since the run joins from one
    # address twice within their seconds, and on a proxy the stand-ins as its servers. This function
    # runs inside an if, where set -e does not reach, so each edit says when it fails.
    boot "$target" "$project" "$jdk" "$dir" || return 1
    stop_target "$target"
    case "$project" in
        paper | folia)
            sed -i 's/proxy-protocol: false/proxy-protocol: true/' "$dir/config/paper-global.yml" || return 1
            sed -i 's/connection-throttle: [0-9]*/connection-throttle: -1/' "$dir/bukkit.yml" || return 1
            # The staff member who watches chat for the refusal.
            echo "[{\"uuid\":\"$(java "$ROOT/e2e/Probe.java" uuid staff)\",\"name\":\"staff\",\"level\":4," \
                "\"bypassesPlayerLimit\":false}]" > "$dir/ops.json" || return 1
            ;;
        velocity)
            sed -i -e 's/^online-mode = true/online-mode = false/' \
                -e 's/^haproxy-protocol = false/haproxy-protocol = true/' \
                -e 's/^login-ratelimit = .*/login-ratelimit = 0/' \
                -e "s/^bind = .*/bind = \"0.0.0.0:$port\"/" \
                -e "s/^lobby = .*/lobby = \"127.0.0.1:${BACKENDS[0]#*:}\"/" \
                -e "s/^factions = .*/eu = \"127.0.0.1:${BACKENDS[1]#*:}\"/" \
                -e "s/^minigames = .*/na = \"127.0.0.1:${BACKENDS[2]#*:}\"/" \
                -e 's/"factions"/"eu"/; s/"minigames"/"na"/' "$dir/velocity.toml" || return 1
            ;;
        bungeecord)
            sed -i -e 's/online_mode: true/online_mode: false/' \
                -e 's/proxy_protocol: false/proxy_protocol: true/' \
                -e 's/connection_throttle: [0-9]*/connection_throttle: 0/' \
                -e "s/host: 0.0.0.0:25577/host: 0.0.0.0:$port/" \
                -e "s/address: localhost:25565/address: 127.0.0.1:${BACKENDS[0]#*:}/" \
                -e "/^servers:/a\\  eu:\\n    motd: eu\\n    address: 127.0.0.1:${BACKENDS[1]#*:}" \
                -e "/^servers:/a\\  na:\\n    motd: na\\n    address: 127.0.0.1:${BACKENDS[2]#*:}" \
                "$dir/config.yml" || return 1
            ;;
    esac

    # The shipped config, with no key, one country refused, and on a proxy two continents routed.
    sed -e 's/^  block: \[\]/  block: [CN]/' \
        "$ROOT/core/src/main/resources/io/internetdata/minecraft/config.yml" > "$data/config.yml" || return 1
    if [[ "$proxy" == y ]]; then
        sed -i 's/^  continents: {}/  continents: {EU: eu, NA: na}/' "$data/config.yml" || return 1
    fi
    echo "8.8.4.4  # staff" > "$data/bypass.txt" || return 1

    : > "$dir/stub.log"
    java "$ROOT/e2e/StubApi.java" "$STUB_PORT" "$dir/stub.log" > "$dir/stub.out" 2>&1 &
    STUB_PID=$!
    wait_for "$dir/stub.out" "listening on" 60
    if [[ "$proxy" == y ]]; then
        : > "$dir/backends.log"
        java "$ROOT/e2e/Backend.java" "$dir/backends.log" "${BACKENDS[@]}" > "$dir/backends.out" 2>&1 &
        BACKEND_PID=$!
        wait_for "$dir/backends.out" "listening" 60
    fi

    boot "$target" "$project" "$jdk" "$dir" || return 1
    wait_for "$dir/console.log" "InternetData $VERSION on " 60 || return 1

    # On Paper an admitted player is in; Velocity lets them in, then sends them to a stand-in, which
    # turns them away; BungeeCord sends them on before it finishes their login, so a player it let
    # in is turned away by the stand-in itself.
    local admitted="SUCCESS" mode=""
    if [[ "$project" == "bungeecord" ]]; then
        admitted="DISCONNECT"
    elif [[ "$project" == "velocity" ]]; then
        mode="follow"
    fi
    local ok=0
    local watcher=""
    if [[ "$version" == "1.20.1" ]]; then
        # Only 1.20.1's login ends in play, where the probe can stay and read chat as staff.
        probe "$port" staff 8.8.4.4 watch Refused bot2 20 > "$dir/watch.out" &
        watcher=$!
        wait_for "$dir/console.log" "staff joined the game" 30 || echo "  FAIL staff never joined"
    fi
    expect "Tor is refused" "DISCONNECT" "Tor" probe "$port" bot1 45.83.91.1 || ok=1
    expect "a refused country gets its own message" "DISCONNECT" "accept players from your country" \
        probe "$port" bot2 36.110.1.2 || ok=1
    if [[ -n "$watcher" ]]; then
        wait "$watcher" || true
        if [[ "$(cat "$dir/watch.out")" == "SAW" ]]; then
            echo "  ok   a staff member sees the refusal in chat"
        else
            echo "  FAIL staff saw no refusal in chat: $(cat "$dir/watch.out")"
            ok=1
        fi
    fi
    expect "a home connection gets in" "$admitted" "$(admitted_text "$project" lobby)" \
        probe "$port" alex 177.1.2.3 $mode || ok=1
    expect "an anycast connection gets in" "$admitted" "$(admitted_text "$project" na)" \
        probe "$port" sky 98.97.1.2 $mode || ok=1
    expect "a datacenter gets in with no key" "$admitted" "$(admitted_text "$project" lobby)" \
        probe "$port" host 146.70.116.130 $mode || ok=1
    expect "a bypassed address gets in" "$admitted" "$(admitted_text "$project" lobby)" \
        probe "$port" staff 8.8.4.4 $mode || ok=1
    expect "a private address gets in" "$admitted" "$(admitted_text "$project" lobby)" \
        probe "$port" lan 10.1.2.3 $mode || ok=1
    expect "the same Tor exit again is refused" "DISCONNECT" "Tor" probe "$port" bot3 45.83.91.1 || ok=1
    if [[ "$proxy" == y ]]; then
        expect "a home connection in Germany gets in" "$admitted" "$(admitted_text "$project" eu)" \
            probe "$port" jan 84.128.1.2 $mode || ok=1
        expect_first "$dir/backends.log" jan eu "routing sends a player in Europe to eu" || ok=1
        expect_first "$dir/backends.log" sky na "routing sends a player in North America to na" || ok=1
        expect_first "$dir/backends.log" alex lobby "a player routing doesn't name goes to the default" || ok=1
        expect_first "$dir/backends.log" staff lobby "a bypassed player goes to the default" || ok=1
    fi

    expect_count "$dir/stub.log" "/45.83.91.1" 1 "a refused address is looked up once, then cached" || ok=1
    expect_count "$dir/stub.log" "/146.70.116.130" 1 "a datacenter is looked up once" || ok=1
    expect_count "$dir/stub.log" "/8.8.4.4" 0 "a bypassed address is never looked up" || ok=1
    expect_count "$dir/stub.log" "/10.1.2.3" 0 "a private address is never looked up" || ok=1
    expect_count "$dir/stub.log" "/api/v1/entitlement" 0 "with no key, no plan is asked for" || ok=1
    if grep -q "checks.hosting is block, but it needs a paid plan or a free key" "$dir/console.log"; then
        echo "  ok   the console says hosting needs a paid plan or a free key's full answers"
    else
        echo "  FAIL the console never warned that hosting isn't answered with no key"
        ok=1
    fi
    if grep -q "internetdata-minecraft/$VERSION (" "$dir/stub.log"; then
        echo "  ok   the User-Agent names the plugin: $(cut -f2 "$dir/stub.log" | sort -u | head -1)"
    else
        echo "  FAIL no request named the plugin in its User-Agent: $(cut -f2 "$dir/stub.log" | sort -u)"
        ok=1
    fi
    if grep -q "Refused bot1 from 45.83.91.1 (DE): Tor" "$dir/console.log"; then
        echo "  ok   the console logs the refusal"
    else
        echo "  FAIL the console never logged bot1's refusal"
        ok=1
    fi
    if grep -q "sky joined from 98.97.1.2 (US): anycast (SpaceX Services, Inc.)" "$dir/console.log"; then
        echo "  ok   the console logs the anycast connection flagged to staff"
    else
        echo "  FAIL the console never logged sky's flag"
        ok=1
    fi
    return "$ok"
}

admitted_text() {
    if [[ "$1" == "bungeecord" ]]; then
        echo "backend $2"
    fi
}

probe() {
    java "$ROOT/e2e/Probe.java" 127.0.0.1 "$@"
}

# expect <what> <SUCCESS|DISCONNECT> <text the outcome must hold> <command...>
expect() {
    local what=$1 outcome=$2 text=$3
    shift 3
    local got
    got=$("$@" || true)
    if [[ "$got" == "$outcome"* && "$got" == *"$text"* ]]; then
        echo "  ok   $what"
        return 0
    fi
    echo "  FAIL $what: $got"
    return 1
}

# expect_first <backends log> <player> <server> <what>: the first server the player reached.
expect_first() {
    local log=$1 player=$2 want=$3 what=$4
    local got
    got=$(grep -m1 " $player\$" "$log" | cut -d' ' -f1 || true)
    if [[ "$got" == "$want" ]]; then
        echo "  ok   $what"
        return 0
    fi
    echo "  FAIL $what: $player first reached ${got:-no server}"
    return 1
}

expect_count() {
    local log=$1 path=$2 want=$3 what=$4
    local got
    got=$(cut -f1 "$log" | grep -cx "$path" || true)
    if [[ "$got" == "$want" ]]; then
        echo "  ok   $what"
        return 0
    fi
    echo "  FAIL $what: $path was asked $got times"
    return 1
}

boot() {
    local target=$1 project=$2 jdk=$3 dir=$4
    local image
    image=$(java_image "$jdk")
    # Only Paper's command line takes --nogui; Velocity refuses an option it doesn't know.
    local args=()
    if [[ "$project" == "paper" || "$project" == "folia" ]]; then
        args=(--nogui)
    fi
    : > "$dir/console.log"
    docker rm -f "inetd-e2e-$target" > /dev/null 2>&1 || true
    # As the user running this, so the files the server writes stay editable here: on a CI runner,
    # which is not root, a root container's config files could not be changed between the starts.
    docker run -d --name "inetd-e2e-$target" --network host --user "$(id -u):$(id -g)" -e HOME=/srv \
        -v "$dir:/srv" -w /srv "$image" \
        java -Xmx1G "-Dinternetdata.api-url=http://127.0.0.1:$STUB_PORT" -jar server.jar "${args[@]}" > /dev/null
    docker logs -f "inetd-e2e-$target" > "$dir/console.log" 2>&1 &
    if ! wait_for "$dir/console.log" "Done \\(|Listening on " 300; then
        echo "  the server never finished starting; its log ends:"
        tail -20 "$dir/console.log" | sed 's/^/    /'
        return 1
    fi
}

stop_target() {
    docker stop -t 60 "inetd-e2e-$1" > /dev/null 2>&1 || true
    docker rm -f "inetd-e2e-$1" > /dev/null 2>&1 || true
    local pid
    for pid in "${STUB_PID:-}" "${BACKEND_PID:-}"; do
        if [[ -n "$pid" ]]; then
            kill "$pid" 2> /dev/null || true
            wait "$pid" 2> /dev/null || true
        fi
    done
    STUB_PID=
    BACKEND_PID=
}

cleanup() {
    local line
    for line in "${TARGETS[@]}"; do
        read -r target _ <<< "$line"
        docker rm -f "inetd-e2e-$target" > /dev/null 2>&1 || true
    done
    stop_target none
}

wait_for() {
    local file=$1 pattern=$2 seconds=$3
    local i
    for ((i = 0; i < seconds * 4; i++)); do
        if grep -qE "$pattern" "$file" 2> /dev/null; then
            return 0
        fi
        sleep 0.25
    done
    return 1
}

java_image() {
    case "$1" in
        17) echo "eclipse-temurin:17.0.20_8-jre-noble" ;;
        21) echo "eclipse-temurin:21.0.12_8-jre-noble" ;;
        25) echo "eclipse-temurin:25.0.4_7-jre-noble" ;;
    esac
}

# Prints the path of a server's jar, downloading it the first time: PaperMC's builds checked against
# the SHA-256 their API publishes, and BungeeCord's latest successful build from its CI.
download() {
    local project=$1 version=$2
    local out=$CACHE/$project-$version.jar
    if [[ -s "$out" ]]; then
        echo "$out"
        return 0
    fi
    if [[ "$project" == "bungeecord" ]]; then
        curl -fsSL -o "$out.part" \
            "https://hub.spigotmc.org/jenkins/job/BungeeCord/lastSuccessfulBuild/artifact/bootstrap/target/BungeeCord.jar"
    else
        local build url sum
        build=$(curl -fsS "https://fill.papermc.io/v3/projects/$project/versions/$version/builds/latest")
        url=$(jq -r '.downloads."server:default".url' <<< "$build")
        sum=$(jq -r '.downloads."server:default".checksums.sha256' <<< "$build")
        curl -fsSL -o "$out.part" "$url"
        echo "$sum  $out.part" | sha256sum -c --quiet - >&2
    fi
    mv "$out.part" "$out"
    echo "$out"
}

main "$@"
