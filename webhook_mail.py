import sys
import html
import boto3
from botocore.exceptions import ClientError
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

# Webhook-monitor version of mail.py: sends a plain alert (no test-results attachment)
# to the Scala dev addresses, via SES, from the same sender as mail.py.
#
# Usage: /bin/python3.6 webhook_mail.py "<subject>" <body-file>

#Variables
SENDER = 'jenkins@no-reply.scalacomputing.com'
AWS_REGION = "us-east-1"
SCALA_DEV_EMAIL="vlakshmanan@scalacomputing.com"
SCALA="ncar-dev@scalacomputing.com"
CHARSET = "utf-8"

if len(sys.argv) != 3:
    print('usage: webhook_mail.py "<subject>" <body-file>')
    sys.exit(2)

SUBJECT = sys.argv[1]
with open(sys.argv[2], "r") as f:
    BODY = f.read()
print(BODY)

HTML_BODY = """
<html>
<head></head>
<body>
<pre style="font-family: monospace; font-size: 13px;">{}</pre>
</body>
</html>""".format(html.escape(BODY))

client = boto3.client('ses', region_name=AWS_REGION)
msg = MIMEMultipart('mixed')
msg['Subject'] = SUBJECT
msg['From'] = SENDER
msg['To'] = SCALA_DEV_EMAIL
msg['Cc'] = SCALA

msg_body = MIMEMultipart('alternative')
msg_body.attach(MIMEText(BODY.encode(CHARSET), 'plain', CHARSET))
msg_body.attach(MIMEText(HTML_BODY.encode(CHARSET), 'html', CHARSET))
msg.attach(msg_body)

try:
    response = client.send_raw_email(
        Source=SENDER,
        Destinations=[
            SCALA_DEV_EMAIL, SCALA
        ],
        RawMessage={
            'Data': msg.as_string(),
        },
    )
except ClientError as e:
    print(e.response['Error']['Message'])
    sys.exit(1)
else:
    print("Email sent! Message ID:"),
    print(response['MessageId'])
