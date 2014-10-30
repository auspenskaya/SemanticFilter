#!/bin/bash
git log --name-status -25 | grep -i "^[AM]" | awk '{print $2}' | sort | uniq -u 
