import json
import base64
import zlib
import io
import gzip

html_content = '''<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\">
  <script type=\"__bundler/manifest\">
{
  \"aad82505-d661-4ef2-9bca-878be79f49c0\": {
    \"mime\": \"text/html\",
    \"compressed\": true,
    \"data\": \"H4sIAAAAAAAACry865LiSJIo/H+eQlNzzmTmKDPFRUgiq6vbQEggEAgQEhI9vW26hC6gG7qL7jbbh9hn+B5sn+SzkICErJw5s7vHTrW1lZAiPDw8/O4e9cOfRwK9UZcM4qS+9+Offrj8BTTzxz8hyA8+SDXEcLQ4Aem3L1lqvVBf6g+pm3rgRz60ETf4AWt+wfdJWjVPCPI35DfE12LbDd6Q1lck0kzTDez6WQ/Ll8Q91T/1MDZB/KKH5Vfkj3qiHpoV8huia8bBjsMsMN+Qv1ia1bd6XxHTTSJPq94QywPlV0TzXDt4cVPgJ2+IAYIUxF+RfZakrlW9GGGQgiB9/+C7wYsDXNtJ35B2q5U7XxErDNIXS/Ndr3pDXrQo8sBLUiUp8J+RoecGh7lmiPVvNgzSZyTRguQlAbFrXbD9y6+/6llgeiD+1Qs1uEXkNyQKEzd1w+ANsdwSmHDHeadroNames = [...]\"
  },
  \"935b0312-cdff-4e5d-ba16-a6a5513f1970\": {
    \"mime\": \"text/html\",
    \"compressed\": true,
    \"data\": \"...\"
  },
  \"916e7dfa-feda-4abe-a3c3-070982b2735e\": {
    \"mime\": \"text/html\",
    \"compressed\": true,
    \"data\": \"...\"
  },
  \"ecde06fd-b555-4e91-8822-aced34d420f6\": {
    \"mime\": \"text/html\",
    \"compressed\": true,
    \"data\": \"...\"
  }
}
  </script>
</head>
</html>
'''
print("Script template")
