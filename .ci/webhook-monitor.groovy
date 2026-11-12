#!groovy
// WRF-Webhook-Delivery-Monitor
//
// Every 15 minutes, reads GitHub's delivery log for the wrf-model/WRF webhook that
// triggers WRF-Feature-Regression-Test, and emails an alert for any delivery that
// failed (e.g. "failed to connect to host") and was not later redelivered OK.
// Read-only: it never redelivers or changes anything on GitHub.
//
// Token handling is the same as build.groovy: the 'ncar-git-token' Jenkins credential
// is bound with withCredentials as $gitToken (masked as **** in logs).
// Single-quoted sh blocks are used so the shell, not Groovy, expands $gitToken.

pipeline {
    agent any

    triggers {
        cron('H/15 * * * *')
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '300'))
    }

    environment {
        REPO       = 'wrf-model/WRF'
        HOOK_ID    = '340938403'
        STATE_DIR  = '/var/lib/jenkins/webhook-monitor'
        JENKINS_UI = 'https://ncar_jenkins.scalacomputing.com'
    }

    stages {
        stage('Check webhook deliveries') {
            steps {
                script {
                    def rc
                    withCredentials([string(credentialsId: 'ncar-git-token', variable: 'gitToken')]) {
                        rc = sh(returnStatus: true, script: '''#!/bin/bash
set -uo pipefail
mkdir -p "$STATE_DIR"
touch "$STATE_DIR/alerted.txt"
W="$WORKSPACE/run"; rm -rf "$W"; mkdir -p "$W"

gh() {   # gh <api path> <outfile> -> 0 on HTTP 200 (retries GitHub's transient 5xx)
  local code i
  for i in 1 2 3 4 5; do
    code=$(curl -s -m 60 -o "$2" -w '%{http_code}' \
           -H "Authorization: token $gitToken" -H "Accept: application/vnd.github+json" \
           "https://api.github.com$1")
    if [ "$code" = 200 ]; then
      # Delivery IDs are 19-digit numbers; jq 1.5 (CentOS 7) rounds them as floats
      # (3853158501557731328 -> 3853158501557731300). Quote them so jq keeps them exact.
      # (\\1 is doubled because this script is inside a Groovy string.)
      sed -i -E 's/"id": *([0-9]{16,})/"id":"\\1"/g' "$2"
      jq empty "$2" 2>/dev/null || { echo "GitHub API $1 -> response is not valid JSON"; return 1; }
      return 0
    fi
    case "$code" in 5*|000) sleep $((i * 5)) ;; *) break ;; esac
  done
  echo "GitHub API $1 -> HTTP $code: $(head -c 300 "$2" 2>/dev/null)"
  return 1
}

get_mailer() {
  git clone -q --depth 1 https://github.com/scala-computing/jenkins-auto.git "$W/jenkins-auto"
}

mail() {   # mail <subject> <bodyfile>  -> SCALA_DEV_EMAIL + SCALA (set in webhook_mail.py)
  # Prefer the copy in jenkins-auto (like mail.py); fall back to the server copy until it's committed.
  local m="$STATE_DIR/webhook_mail.py"
  get_mailer && [ -f "$W/jenkins-auto/webhook_mail.py" ] && m="$W/jenkins-auto/webhook_mail.py"
  sudo -S /bin/python3.6 "$m" "$1" "$2"
}

# --- 1. fetch the delivery log ------------------------------------------------
if ! gh "/repos/$REPO/hooks/$HOOK_ID/deliveries?per_page=100" "$W/deliveries.json"; then
  # Alert once when the API starts failing (token revoked/expired, permissions, ...)
  if [ ! -f "$STATE_DIR/api_failing" ]; then
    touch "$STATE_DIR/api_failing"
    cat > "$W/body.txt" <<EOF
The WRF webhook delivery monitor could not read GitHub's webhook delivery log.

Most likely the Jenkins credential 'ncar-git-token' has expired, been revoked, or lost the
admin:repo_hook permission. Until this is fixed, missed webhook deliveries will NOT be detected.

Console: $JENKINS_UI/job/$JOB_NAME/$BUILD_NUMBER/console
EOF
    mail "WRF webhook monitor: cannot read GitHub delivery log" "$W/body.txt"
  fi
  exit 4
fi
rm -f "$STATE_DIR/api_failing"

total=$(jq 'length' "$W/deliveries.json")
echo "Deliveries returned by GitHub: $total"
case "$total" in ''|*[!0-9]*) echo "Could not parse the delivery log"; exit 4 ;; esac

# GUIDs that eventually got a 2xx (a successful redelivery reuses the GUID)
jq -r '[.[] | select(.status_code >= 200 and .status_code < 300) | .guid] | unique | .[]' \
   "$W/deliveries.json" > "$W/ok_guids"

# Failed deliveries: id guid time event action code status...
jq -r '.[] | select(.event != "ping") | select(.status_code < 200 or .status_code >= 300)
       | "\\(.id) \\(.guid) \\(.delivered_at) \\(.event) \\(.action // "-") \\(.status_code) \\(.status)"' \
   "$W/deliveries.json" > "$W/failed"
echo "Failed deliveries in log: $(wc -l < "$W/failed")"

# --- 2. find failures we haven't alerted about yet ------------------------------
new=0; needs_test=0
: > "$W/report"
while read -r id guid when event action code status; do
  grep -qxF "$guid" "$W/ok_guids" && continue          # later redelivered OK
  grep -qxF "$id"   "$STATE_DIR/alerted.txt" && continue # already alerted
  pr="?"; sha="?"; title=""; label=""
  if gh "/repos/$REPO/hooks/$HOOK_ID/deliveries/$id" "$W/d.json"; then
    pr=$(jq -r '.request.payload.number // .request.payload.pull_request.number // "?"' "$W/d.json")
    sha=$(jq -r '.request.payload.pull_request.head.sha // "?"' "$W/d.json" | cut -c1-8)
    title=$(jq -r '.request.payload.pull_request.title // ""' "$W/d.json")
    label=$(jq -r '.request.payload.label.name // ""' "$W/d.json")
  fi
  case "$action" in
    opened|synchronize|reopened) tag="TESTS DID NOT RUN"; needs_test=$((needs_test+1)) ;;
    labeled) if [ "$label" = "Retest" ]; then tag="TESTS DID NOT RUN"; needs_test=$((needs_test+1)); else tag="no test needed"; fi ;;
    *) tag="no test needed" ;;
  esac
  {
    echo "- $when  $event.$action  PR #$pr ($sha)  [$tag]"
    [ -n "$title" ] && echo "    $title"
    echo "    GitHub said: HTTP $code - $status"
    [ "$tag" = "TESTS DID NOT RUN" ] && echo "    Fix: sudo /root/run-wrf-test.sh $pr   (on the Jenkins server)"
  } >> "$W/report"
  echo "$id" >> "$STATE_DIR/alerted.txt"
  new=$((new+1))
done < "$W/failed"

tail -n 2000 "$STATE_DIR/alerted.txt" > "$STATE_DIR/alerted.tmp" && mv "$STATE_DIR/alerted.tmp" "$STATE_DIR/alerted.txt"

if [ "$new" -eq 0 ]; then
  echo "No new failed deliveries."
  exit 0
fi

echo "New failed deliveries: $new (needing tests: $needs_test)"
cat "$W/report"

subject="WRF webhook: $new failed GitHub deliveries"
[ "$needs_test" -gt 0 ] && subject="WRF webhook: $needs_test PR event(s) missed - regression tests did not run"
cat > "$W/body.txt" <<EOF
GitHub failed to deliver $new webhook event(s) to Jenkins (wrf-model/WRF, hook $HOOK_ID).
Events marked "TESTS DID NOT RUN" need the regression tests started by hand.

$(cat "$W/report")

GitHub does not retry failed deliveries automatically.
"failed to connect to host" usually means the request was blocked before it reached Jenkins
(e.g. the load balancer security group doesn't allow GitHub's current webhook IP ranges -
compare with https://api.github.com/meta "hooks").

Monitor run: $JENKINS_UI/job/$JOB_NAME/$BUILD_NUMBER/console
EOF
mail "$subject" "$W/body.txt"
exit 3
''')
                    }
                    if (rc == 3) {
                        currentBuild.result = 'UNSTABLE'   // new failed deliveries found and emailed
                    } else if (rc != 0) {
                        error("Webhook delivery check failed (exit ${rc})")
                    }
                }
            }
        }
    }
}
