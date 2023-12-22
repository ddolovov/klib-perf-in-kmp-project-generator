#!/usr/bin/env python3

import os
from datetime import datetime
import re
import statistics

now = datetime.now()
measurementsDir = 'measurements'
timingsFile = os.path.join(measurementsDir, 'timings.txt')
resultFile = os.path.join(measurementsDir, 'result.txt')
rounds = 20

if os.path.exists(measurementsDir):
  backupDir = measurementsDir + '.' + now.strftime("%Y%m%d-%H%M%S")
  print("Previous measurements found in '" + measurementsDir + "'. Renaming the directory to '" + backupDir + "'.")
  print()
  os.rename(measurementsDir, backupDir)

os.mkdir(measurementsDir)

print("Running a serie of " + str(rounds) + " rounds.")

for round in range(1, rounds + 1):
  print()
  print("Round " + str(round) + " of " + str(rounds))
  stdoutFile = "./" + measurementsDir + "/round_" + str(round) + "_stdout.log"
  stderrFile = "./" + measurementsDir + "/round_" + str(round) + "_stderr.log"
  os.system("./gradlew measureCompileTime 1>" + stdoutFile + " 2>" + stderrFile)
  with open(stdoutFile) as fi:
    for line in fi:
      match = re.search("measureCompileTime finished in ", line.rstrip())
      if match:
        print(match.string[match.start():])
        with open(timingsFile, "a") as fo:
          fo.write(match.string[match.end():].rstrip('s') + "\n")

  print("Round " + str(round) + ": Done.")

print()
with open(timingsFile) as fi:
  timings = [float(line.rstrip()) for line in fi]
  mean = format(statistics.mean(timings), ".2f")
  stddev = format(statistics.stdev(timings), ".2f")
  print("Average value: " + mean + " +/- " + stddev)
  print()
  with open(resultFile, 'w') as fo:
    fo.write(mean + "\n")
    fo.write(stddev + "\n")
