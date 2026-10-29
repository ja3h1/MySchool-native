import os, glob, zlib, struct

def unzoom_png(filepath, scale_factor=0.833333333):
    with open(filepath, 'rb') as f:
        data = f.read()

    if not data.startswith(b'\x89PNG\r\n\x1a\n'):
        return

    # Parse chunks
    idx = 8
    chunks = []
    width = height = bit_depth = color_type = 0
    idat_bytes = bytearray()

    while idx < len(data):
        length, chunk_type = struct.unpack('>I4s', data[idx:idx+8])
        chunk_data = data[idx+8:idx+8+length]
        crc = data[idx+8+length:idx+12+length]
        idx += 12 + length

        if chunk_type == b'IHDR':
            width, height, bit_depth, color_type, comp, filt, inter = struct.unpack('>IIBBBBB', chunk_data)
        elif chunk_type == b'IDAT':
            idat_bytes.extend(chunk_data)
        elif chunk_type == b'IEND':
            pass

    if color_type != 6 or bit_depth != 8: # Only handle 8-bit RGBA
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
            if filter_type == 1: # Sub
                recon += raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
            elif filter_type == 2: # Up
                recon += prev_line[x]
            elif filter_type == 3: # Average
                left = raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
                recon += (left + prev_line[x]) // 2
            elif filter_type == 4: # Paeth
                left = raw_pixels[y * width * bpp + x - bpp] if x >= bpp else 0
                up = prev_line[x]
                corner = prev_line[x - bpp] if x >= bpp else 0
                p = left + up - corner
                pa = abs(p - left)
                pb = abs(p - up)
                pc = abs(p - corner)
                if pa <= pb and pa <= pc: pr = left
                elif pb <= pc: pr = up
                else: pr = corner
                recon += pr

            raw_pixels[y * width * bpp + x] = recon % 256
            prev_line[x] = raw_pixels[y * width * bpp + x]

    # Unzoom (shrink and pad with transparent pixels)
    new_w = int(width * scale_factor)
    new_h = int(height * scale_factor)
    off_x = (width - new_w) // 2
    off_y = (height - new_h) // 2

    unzoomed_pixels = bytearray(height * width * bpp) # All 0s (transparent)
    for y in range(new_h):
        dest_y = off_y + y
        src_y = int(y / scale_factor)
        src_y = min(max(src_y, 0), height - 1)
        for x in range(new_w):
            dest_x = off_x + x
            src_x = int(x / scale_factor)
            src_x = min(max(src_x, 0), width - 1)

            for c in range(bpp):
                unzoomed_pixels[(dest_y * width + dest_x) * bpp + c] = raw_pixels[(src_y * width + src_x) * bpp + c]

    # Compress with Filter 0 (None)
    new_decompressed = bytearray()
    for y in range(height):
        new_decompressed.append(0) # Filter None
        new_decompressed.extend(unzoomed_pixels[y * width * bpp : (y + 1) * width * bpp])

    new_idat = zlib.compress(bytes(new_decompressed))

    def make_chunk(ctype, cdata):
        res = struct.pack('>I', len(cdata)) + ctype + cdata
        crc = zlib.crc32(ctype + cdata)
        return res + struct.pack('>I', crc)

    out = b'\x89PNG\r\n\x1a\n'
    out += make_chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, bit_depth, color_type, 0, 0, 0))
    out += make_chunk(b'IDAT', new_idat)
    out += make_chunk(b'IEND', b'')

    with open(filepath, 'wb') as f:
        f.write(out)
    print(f"Reverted {filepath}")

# Process all launcher icons
png_files = glob.glob("app/src/main/res/mipmap-*/ic_launcher*.png") + glob.glob("app/src/main/ic_launcher*.png")
for p in png_files:
    unzoom_png(p, scale_factor=1/1.20)
