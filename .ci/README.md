# CI scripts

Pipelines and infrastructure for the WRF regression tests on `ncar_jenkins.scalacomputing.com`.

| Path | What it is |
|---|---|
| `build.groovy` | Pipeline for the **`WRF-Feature-Regression-Test`** job. Triggered by the wrf-model/WRF webhook; builds WRF on EC2 test servers, runs the test cases, compares output bit-for-bit, posts a status to the PR and emails the results. |
| `webhook-monitor.groovy` | Pipeline for the **`WRF-Webhook-Delivery-Monitor`** job. Every 15 minutes it checks whether GitHub managed to deliver its webhook events to Jenkins, and emails an alert when one failed. See below. |
| `terraform/` | Terraform and per-test-case scripts that `build.groovy` uses to create the EC2 test servers (`wrf_testcase_N.sh`, one per test case). |
| `../mail.py`, `../SESEmailHelper.py` | Email helpers used by `build.groovy` (results to the PR author, NCAR and Scala). |
| `../webhook_mail.py` | Email helper used by `webhook-monitor.groovy` (alerts to Scala only). |

Jenkins does **not** read these pipelines from the repo. Each job holds a copy of its
`.groovy` file in its configuration (*Configure → Pipeline script*). After changing a
file here, paste the new version into the job, or update it with the Jenkins REST API
(`POST /job/<name>/config.xml`).

## Webhook delivery monitor

### Why

GitHub does not retry a failed webhook delivery. If a `pull_request` event never reaches
Jenkins (firewall change, load balancer down, Jenkins restarting), that PR's regression
tests silently never run and nothing in Jenkins shows it. The monitor closes that gap:
a missed event is reported by email within about 15 minutes.

### What it does

1. Reads the delivery log of the wrf-model/WRF webhook from GitHub's API.
2. Keeps the deliveries that failed (non-2xx), dropping any that were later redelivered
   successfully, any already reported (remembered in `alerted.txt`), and `ping` events.
3. For each new failure, looks up the PR number, commit and title and decides whether the
   event would have started the tests (`opened`, `synchronize`, `reopened`, or `labeled`
   while the PR carries the `Retest` label, the same rule as `build.groovy`).
4. Sends one email covering everything new, then records those deliveries as reported.

It is read-only: it never redelivers an event or starts a test. The email contains the
command to re-run the tests by hand (see *Re-running tests by hand*).

### Prerequisites

| Requirement | Detail |
|---|---|
| Jenkins credential `ncar-git-token` | A GitHub personal access token stored as a *Secret text* credential. It is the token `build.groovy` uses to post PR statuses. The monitor needs it to have the **`admin:repo_hook`** scope on wrf-model/WRF as well, because reading a webhook's delivery log is an admin-only API. `build.groovy` only uses the token for statuses, so a working test job does not prove the scope is present; check with `curl -H "Authorization: token …" https://api.github.com/repos/wrf-model/WRF/hooks/340938403/deliveries?per_page=1` (expect HTTP 200). |
| Webhook id `340938403` | The id of the wrf-model/WRF webhook that points at Jenkins. It is set as `HOOK_ID` at the top of `webhook-monitor.groovy`. If the webhook is ever deleted and re-created it gets a new id; find it with `GET /repos/wrf-model/WRF/hooks` and update `HOOK_ID`. |
| State directory `/var/lib/jenkins/webhook-monitor/` | Must exist on the Jenkins host and be writable by the `jenkins` user. It holds `alerted.txt` (delivery ids already emailed about), `api_failing` (present while GitHub cannot be read, so the "cannot read" email goes out once per outage) and a fallback copy of `webhook_mail.py`. The job refuses to run (red) if the directory is not writable, so it can never lose track of what it has reported. |
| `webhook_mail.py` reachable | The job clones this repo and runs `webhook_mail.py` from it; if the clone fails it uses the copy in the state directory. The script runs with `/bin/python3.6` and needs `boto3` and SES credentials on the host, exactly like `mail.py`. |
| `jq` and `curl` on the Jenkins host | Already there for `build.groovy`. Note the host's `jq` is 1.5, which rounds GitHub's 19-digit delivery ids; the script quotes them before parsing to keep them exact. |

### Creating the job (one time)

Merging a change to this repo does not create or update the job. To create it:

1. *New Item* → name `WRF-Webhook-Delivery-Monitor` → *Pipeline*.
2. Paste the contents of `webhook-monitor.groovy` into *Pipeline script*, save.
3. On the Jenkins host, as `jenkins`: `mkdir -p /var/lib/jenkins/webhook-monitor` and copy
   `webhook_mail.py` into it. To avoid an email about failures that were already handled,
   put their delivery ids in `/var/lib/jenkins/webhook-monitor/alerted.txt`, one per line.
4. Click *Build Now* once. Jenkins only activates a pipeline's `cron` trigger after the
   first run.

The job currently exists on `ncar_jenkins` and was created this way on 2026-11-12.

### Reading the job status

| Colour | Exit code | Meaning | Email sent? |
|---|---|---|---|
| Green (SUCCESS) | 0 | No new failed deliveries | No |
| Yellow (UNSTABLE) | 3 | New failed deliveries found and emailed | Yes, one per run; each delivery is reported once |
| Red (FAILURE) | 4 | GitHub's delivery log could not be read (token expired or revoked, `admin:repo_hook` missing, wrong hook id, GitHub down) | Yes, once per outage, with GitHub's response |
| Red (FAILURE) | 5 | The monitor itself is broken: state dir not writable, log unparseable, or the email could not be sent | No. Nothing is recorded, so the next successful run reports the same failures again |

Alerts go to `SCALA_DEV_EMAIL` and `SCALA` as set in `webhook_mail.py`, from
`jenkins@no-reply.scalacomputing.com`. GitHub keeps roughly three days of delivery
history and returns at most 100 entries per request; the job prints a warning if it hits
that limit.

### Re-running tests by hand

When the email says `TESTS DID NOT RUN`, start the tests from the Jenkins host:

```
sudo /root/run-wrf-test.sh 2454            # one PR
sudo /root/run-wrf-test.sh 2454 2456       # several PRs
sudo /root/run-wrf-test.sh --dry-run 2454  # show what would happen, start nothing
sudo /root/run-wrf-test.sh --force 2454    # start even if that commit is already being tested
```

The script fetches the PR from GitHub, refuses closed PRs, skips a commit that is already
being tested, and starts `WRF-Feature-Regression-Test` with the same payload GitHub would
have sent for an `opened` event. It reads the Jenkins login from `/root/.wrf-jenkins-cred`.
Check the email's caveat first: `build.groovy` deliberately skips docs-only changes and
PRs labelled `Staging`, `DO_NO_TEST`, `Previous-pipeline` or `Davegill-repo`, so a missed
event for one of those needs no re-run. Each test run uses a set of EC2 instances, so
start only a couple of PRs at a time.

### Known limitations

- The monitor runs on the same personal token as the test pipeline. A dedicated token
  with only `admin:repo_hook` (read) would be better long term; change `credentialsId` in
  `webhook-monitor.groovy` when one exists.
- It watches one webhook on one repository. Other repositories or hooks need their own
  `REPO` and `HOOK_ID`.
