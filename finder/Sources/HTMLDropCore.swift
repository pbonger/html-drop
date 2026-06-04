import AppKit
import Foundation

// MARK: - Upload

func htmlDropUpload(_ html: String, password: String = "") throws -> String {
    let byteCount = html.utf8.count
    guard byteCount <= maxUploadBytes else {
        let mb = String(format: "%.1f", Double(byteCount) / 1024 / 1024)
        throw Fail("\(mb) MB exceeds the \(maxUploadBytes / 1024 / 1024) MB limit")
    }
    var jsonParts = "\"html\":\(jsonEscape(html)),\"ttl\":\"3d\""
    if !password.isEmpty { jsonParts += ",\"password\":\(jsonEscape(password))" }
    let json = "{\(jsonParts)}"
    var req = URLRequest(url: URL(string: uploadURL)!)
    req.httpMethod = "POST"
    req.setValue("application/json", forHTTPHeaderField: "Content-Type")
    req.httpBody = Data(json.utf8)
    let retryDelays: [TimeInterval] = [5, 15, 45]
    for (attempt, delay) in ([0] + retryDelays).enumerated() {
        if delay > 0 { Thread.sleep(forTimeInterval: delay) }
        let (data, resp) = try syncRequest(req)
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if status == 429 {
            if attempt < retryDelays.count { continue }
            throw Fail("Rate limited — please try again later")
        }
        guard (200...299).contains(status) else {
            throw Fail("HTML Drop \(status): \(String(data: data, encoding: .utf8) ?? "")")
        }
        let body = String(data: data, encoding: .utf8) ?? ""
        guard let url = jsonValue(body, key: "url") else {
            throw Fail("No URL in response: \(body)")
        }
        return url
    }
    throw Fail("Upload failed after retries")
}

// MARK: - Clipboard

func copyToClipboard(_ text: String) {
    NSPasteboard.general.clearContents()
    NSPasteboard.general.setString(text, forType: .string)
}

// MARK: - Helpers

struct Fail: Error, LocalizedError {
    let errorDescription: String?
    init(_ msg: String) { errorDescription = msg }
}

func syncRequest(_ req: URLRequest) throws -> (Data, URLResponse) {
    var result: (Data, URLResponse)?; var err: Error?
    let sem = DispatchSemaphore(value: 0)
    URLSession.shared.dataTask(with: req) { d, r, e in
        if let d, let r { result = (d, r) } else { err = e }
        sem.signal()
    }.resume()
    sem.wait()
    if let e = err { throw e }
    return result!
}

func jsonEscape(_ s: String) -> String {
    var out = "\""
    for c in s.unicodeScalars {
        switch c.value {
        case 0x22: out += "\\\""
        case 0x5C: out += "\\\\"
        case 0x0A: out += "\\n"
        case 0x0D: out += "\\r"
        case 0x09: out += "\\t"
        default:
            if c.value < 0x20 { out += String(format: "\\u%04x", c.value) }
            else { out.unicodeScalars.append(c) }
        }
    }
    return out + "\""
}

func jsonValue(_ s: String, key: String) -> String? {
    guard let kr = s.range(of: "\"\(key)\""),
          let cr = s[kr.upperBound...].range(of: ":"),
          let qs = s[cr.upperBound...].range(of: "\"") else { return nil }
    let val = String(s[qs.upperBound...].prefix(while: { $0 != "\"" }))
    return val.isEmpty ? nil : val
}

@discardableResult
func run(_ exe: String, args: [String]) -> Int32 {
    let p = Process(); p.executableURL = URL(fileURLWithPath: exe); p.arguments = args
    try? p.run(); p.waitUntilExit(); return p.terminationStatus
}

extension String {
    var esc: String { replacingOccurrences(of: "\"", with: "\\\"") }
}
