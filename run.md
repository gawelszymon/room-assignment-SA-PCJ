# compilation
javac -cp "lib/pcj-5.3.4.jar" -d . RoomAssignment.java RoomAssignmentPCJ.java

# running executable file and waiting for the output written in the data_result.txt
java -cp ".:lib/pcj-5.3.4.jar" RoomAssignmentPCJ data.txt