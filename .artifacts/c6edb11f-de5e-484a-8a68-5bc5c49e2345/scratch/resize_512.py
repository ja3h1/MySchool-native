import struct, zlib

def resize_to_512(filepath):
    with open(filepath, 'rb') as f:
        data = f.read()

    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        print("Not PNG")
        return

    idx = 8
    idat_bytes = bytearray()
    width = height = bit_depth = color_type = 0

    while idx < len(data):
        length, chunk_type = struct.unpack('>I4s', data[idx:idx+8])
        chunk_data = data[idx+8:idx+8+length]
        idx += 12 + length

        if chunk_type == b'IHDR':
            width, height, bit_depth, color_type, comp, filt, inter = struct.unpack('>IIBBBBB', chunk_data)
        elif chunk_type == b'IDAT':
            idat_bytes.extend(chunk_data)

    print(f"Current size: {width}x{height}")

    if color_type != 6 or bit_depth != 8:
        print(f"Unsupported format: color_type={color_type}, bit_depth={bit_depth}")
        return

    # Decompress scanlines
    decompressed = zlib.decompress(bytes(idat_bytes))
    bpp = 4 # RGBA
    stride = 1 + width * bpp

    # Unfilter
    raw_pixels = bytearray(height * width * bpp)
    prev_line = bytearray(width * bpp)

    for y in range(height):
        filter_type = decompressed[y * stride]
        line = bytearray(decompressed[y * stride + 1 : (y + 1) * stride])

        for x in range(width * bpp):
            recon = line[x]
            if filter_type == 1:
                recon += raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
            elif filter_type == 2:
                recon += prev_line[x]
            elif filter_type == 3:
                left = raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
                recon += (left + prev_line[x]) // 2
            elif filter_type == 4:
                left = raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
                up = prev_line[x]
                corner = prev_line[x - bpp] if x >= bpp else 0
                p = left + up - corner
                pa = abs(p - left); pb = abs(p - up); pc = abs(p - corner)
                if pa <= pb and pa <= pc: pr = left
                elif pb <= pc: pr = up
                else: pr = corner
                recon += pr

            raw_pixels[y * width * bpp + x] = recon % 256
            prev_line[x] = raw_pixels[y * width * bpp + x]

    # Resize to 512x512
    target_w, target_h = 512, 512
    resized_pixels = bytearray(target_h * target_w * bpp)

    for y in range(target_h):
        src_y = int(y * height / target_h)
        src_y = min(max(src_y, 0), height - 1)
        for x in range(target_w):
            src_x = int(x * width / target_w)
            src_x = min(max(src_x, 0), width - 1)

            for c in range(bpp):
                resized_pixels[(y * target_w + x) * bpp + c] = raw_pixels[(src_y * width + src_x) * bpp + c]

    # Compress scanlines with Filter 0 (None)
    new_decompressed = bytearray()
    for y in range(target_h):
        new_decompressed.append(0)
        new_decompressed.extend(resized_pixels[y * target_w * bpp : (y + 1) * target_w * bpp])

    new_idat = zlib.compress(bytes(new_decompressed))

    def make_chunk(ctype, cdata):
        res = struct.pack('>I', len(cdata)) + ctype + cdata
        crc = zlib.crc32(ctype + cdata)
        return res + struct.pack('>I', crc)

    out = b'\x89PNG\r\n\x1a\n'
    out += make_chunk(b'IHDR', struct.pack('>IIBBBBB', target_w, target_h, bit_depth, color_type, 0, 0, 0))
    out += make_chunk(b'IDAT', new_idat)
    out += make_chunk(b'IEND', b'')

    with open(filepath, 'wb') as f:
        f.write(out)
    print(f"Resized {filepath} to 512x512 successfully!")

resize_to_512("app/src/main/ic_launcher-512.png")
