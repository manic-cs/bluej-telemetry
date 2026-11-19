public class Grades {

    public double average(int[] marks) {
        int total = 0;
        for (int mark : marks) {
            total += mark;
        }
        return (double) total / marks.length;
    }

    public int highest(int[] marks) {
        int highest = marks[0];
        for (int mark : marks) {
            if (mark > highest) {
                highest = mark;
            }
        }
        return highest;
    }

    public int countPasses(int[] marks, int passMark) {
        int count = 0;
        for (int mark : marks) {
            if (mark >= passMark) {
                count++;
            }
        }
        return count;
    }
}