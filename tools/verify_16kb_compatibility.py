#!/usr/bin/env python3
"""Fail when an APK contains arm64 native libraries incompatible with 16 KB pages."""
from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path

PAGE_SIZE = 16 * 1024
PT_LOAD = 1


def apk_data_offset(apk_path: Path, info: zipfile.ZipInfo) -> int:
    """Return the start of a ZIP entry's data, including local-header extras."""
    with apk_path.open("rb") as apk:
        apk.seek(info.header_offset)
        header = apk.read(30)
        if len(header) != 30 or header[:4] != b"PK\x03\x04":
            raise ValueError(f"Invalid local ZIP header for {info.filename}")
        name_length, extra_length = struct.unpack_from("<HH", header, 26)
        return info.header_offset + 30 + name_length + extra_length


def elf_load_alignments(binary: bytes) -> list[int]:
    """Read PT_LOAD p_align values from a little-endian ELF32/ELF64 binary."""
    if binary[:4] != b"\x7fELF":
        raise ValueError("not an ELF shared object")
    elf_class = binary[4]
    data_encoding = binary[5]
    if data_encoding != 1:
        raise ValueError("only little-endian ELF is supported")

    if elf_class == 2:  # ELF64
        program_offset = struct.unpack_from("<Q", binary, 32)[0]
        program_entry_size = struct.unpack_from("<H", binary, 54)[0]
        program_count = struct.unpack_from("<H", binary, 56)[0]
        load_alignments = []
        for index in range(program_count):
            entry = program_offset + index * program_entry_size
            program_type = struct.unpack_from("<I", binary, entry)[0]
            if program_type == PT_LOAD:
                load_alignments.append(struct.unpack_from("<Q", binary, entry + 48)[0])
        return load_alignments

    if elf_class == 1:  # ELF32
        program_offset = struct.unpack_from("<I", binary, 28)[0]
        program_entry_size = struct.unpack_from("<H", binary, 42)[0]
        program_count = struct.unpack_from("<H", binary, 44)[0]
        load_alignments = []
        for index in range(program_count):
            entry = program_offset + index * program_entry_size
            program_type = struct.unpack_from("<I", binary, entry)[0]
            if program_type == PT_LOAD:
                load_alignments.append(struct.unpack_from("<I", binary, entry + 28)[0])
        return load_alignments

    raise ValueError(f"unknown ELF class {elf_class}")


def verify(apk_path: Path) -> list[str]:
    failures: list[str] = []
    arm64_libraries = []
    with zipfile.ZipFile(apk_path) as apk:
        for info in apk.infolist():
            if not (info.filename.startswith("lib/arm64-v8a/") and info.filename.endswith(".so")):
                continue
            arm64_libraries.append(info.filename)
            offset = apk_data_offset(apk_path, info)
            if info.compress_type != zipfile.ZIP_STORED:
                failures.append(f"{info.filename}: compressed native library; expected uncompressed packaging")
            if offset % PAGE_SIZE != 0:
                failures.append(
                    f"{info.filename}: ZIP data offset {offset} is not 16 KB aligned "
                    f"(remainder {offset % PAGE_SIZE})"
                )
            try:
                load_alignments = elf_load_alignments(apk.read(info))
            except (OSError, ValueError, struct.error) as error:
                failures.append(f"{info.filename}: cannot inspect ELF headers ({error})")
                continue
            if not load_alignments:
                failures.append(f"{info.filename}: contains no PT_LOAD segments")
            elif any(alignment < PAGE_SIZE for alignment in load_alignments):
                failures.append(
                    f"{info.filename}: PT_LOAD alignments {load_alignments} contain a value below 16384"
                )

    # A 32-bit-only split has no arm64 libraries and therefore no 16 KB page-size
    # compatibility surface to inspect. Arm64-containing APKs remain strictly checked.
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="release APK to validate")
    args = parser.parse_args()

    if not args.apk.is_file():
        print(f"16 KB compatibility check failed: APK not found: {args.apk}", file=sys.stderr)
        return 2

    failures = verify(args.apk)
    if failures:
        print("16 KB compatibility check failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print("16 KB compatibility check passed: all present arm64 native libraries have 16 KB ZIP and ELF alignment.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
