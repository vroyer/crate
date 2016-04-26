#!/usr/bin/env python
# -*- coding: utf-8 -*-

import sys
import tempfile
import atexit
import shutil
import tarfile
import zipfile
import os
from unittest import main as run_tests
from argparse import ArgumentParser


def extract_server_tar(tar_path):
    tmpdir = tempfile.mkdtemp()
    atexit.register(shutil.rmtree, tmpdir, ignore_errors=True)
    with tarfile.open(tar_path) as tar:
        tar.extractall(path=tmpdir)
    return tmpdir


def add_hadoop_libs(hdfs_zip_path, path_to_dist):
    hdfs_plugin_location = os.path.join(
        path_to_dist, 'plugins', 'elasticsearch-repository-hdfs')
    with zipfile.ZipFile(hdfs_zip_path) as hdfs_zip:
        hdfs_zip.extract('hadoop-libs/', path=hdfs_plugin_location)


def main():
    parser = ArgumentParser()
    parser.add_argument('--server-tar', type=str)
    parser.add_argument('--hdfs-zip', type=str)

    args = parser.parse_args()
    print(args)
    server_dist = extract_server_tar(args.server_tar)
    print(server_dist)
    add_hadoop_libs(args.hdfs_zip, server_dist)


if __name__ == "__main__":
    main()
