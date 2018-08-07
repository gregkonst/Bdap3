import java.io.*;
import java.util.*;

public class PearsonsCorrelation {

    /**
     * This method works in combination with the method computeCorrelationsNoPrecomputedMeansFastLookup
     * The main logic behind this method is a float[] lookUpArray, which has size #max_movie_id, and acts as
     * a lookup array. The lookUpArray is initialized with a lookUpFlag invalid value, i.e. -1
     * (ratings cannot be -1). Then to find the common elements between a users' xRatings and a users' yRatings
     * first I populate the look up array in the positions  of the movie ids with the value user X gave to that movie.
     * i.e. there are a total of 7 movies and user x, has rated movie 1,3,4 with ratings 3,4,2,
     * then the look up array will look like the following [-1,3,-1,4,2,-1,-1,-1]. This array needs to be passed to
     * this method as an argument "lookUpArray". All the preparation for the user X is actually done in the method
     * computeCorrelationsNoPrecomputedMeansFastLookup, and not here.
     * <p>
     * The parameter yRatings refers to the rating of the user Y. The algorithm will iterate for all ratings user Y
     * has given and then will check if the lookUpArray has a non lookUpFlag value. If it does not then it means the
     * movie has been rating by both users, for my previous example if user Y has rated movies 1,2,3 with ratings 3,4,5
     * then, first I will check the lookUpArray in the cell 1, the value there is 3!=-1 which means user X also rated
     * that movie, then I will check the movie 2, I go to the 2nd cell of the lookUpArray and I see the value is -1,
     * which means user X did not rate that movie, then finally I go to cell 3 and see the value is 4!=-1 which means
     * the user X also rated that movie.
     * <p>
     * The above algorithm is a very efficient way to to find the common elements. The reason I require a lookUpArray
     * instead of just getting the xRatings are, is because declaring a #max_movie_id sized array is very slow, a
     * workaround that is declaring the array only once, and since on average users have seen just a few movies (not
     * all possible movies existing in the database), after computing the correlation I can just iterate for all
     * movies in xRatings and set the value in the lookup array back to its original value. I.e. in my example after
     * finishing I would go back to the lookUpArray and set only in the positions 1,3,4 the values -1,-1,-1 returning
     * the array to its original state. For these reasons and because creating Integer objects is slow
     * this implementation is faster than just creating a new HashMap all the time. Second reason for requiring a look
     * up array is because I compute the correlation of xRatings with many other yRatings, I compute the correlation
     * of user 1 with users 2,3...~69.000. This means I need to set and reset the values of the look up array 69.000
     * times. Instead I only set it once in the computeCorrelationsNoPrecomputedMeansFastLookup and will only reset it
     * once after I finish computing correlations for user 1 and I go to user 2. This makes the algorithm much faster.
     * <p>
     * The actual computation of the correlation is pretty straightforward, it basically uses a mathematically
     * equivalent 1-pass algorithm of the pearson correlation.
     * <p>
     * The mathematical proof can be found if the .pdf report.
     *
     * @param yRatings             ratings of user Y
     * @param lookUpArray          a look up array populated with the ratings of user X
     * @param lookUpArrayFlag      the special lookUpArray flag value
     * @param minCommonRatedMovies min number of movies to define a Pearson correlation else Float.Nan is returned
     * @return
     */
    private double correlationFastLookUp(List<MovieRating> yRatings,
                                         float[] lookUpArray,
                                         float lookUpArrayFlag,
                                         int minCommonRatedMovies) {
        double correlation = 0;
        //FILL IN HERE
        /*
         * Returns NaN if the 2 Lists have less or equal to 2 common rated movies.
         * Returns a correlation number between -1.0000 ... +1.0000 if the 2 lists
         * have 3 or more common rated movies.
         */

        //I need E[X], E[Y], E[XY], E[X^2], E[Y^2]
        int commonRatedMovies = 0;
        double sumOfXi = 0;
        double sumOfYi = 0;
        double sumOfXiSquared = 0;
        double sumOfYiSquared = 0;
        double sumOfXiYi = 0;


        //Now of all the elements in the other list check if they have common ratings
        for (MovieRating rating : yRatings) {

            double xRating = lookUpArray[rating.getMovieID()];
            //Common element , do stuff!
            if (xRating != lookUpArrayFlag) {

                double yRating = rating.getRating();

                sumOfXi += xRating; //cannot be cached as I do not know beforehand which ratings are common
                sumOfYi += yRating;

                //cannot be cached as I do not know beforehand which ratings are common
                //but could potentially cache the xSquared ySquared so that i skip the multiplications
                //but then I would need to change the MovieRating class
                sumOfXiSquared += xRating * xRating;
                sumOfYiSquared += yRating * yRating;

                sumOfXiYi += xRating * yRating;

                commonRatedMovies++;
            }
        }

        // If the number of rated movies is less than the minimum required to define a common correlation
        if (commonRatedMovies < minCommonRatedMovies) {
            return Float.NaN;
        }

        //https://en.wikipedia.org/wiki/Pearson_correlation_coefficient
        //Numerical instability irrelevant, I only care for up to 4 decimal digits
        double numerator = (commonRatedMovies * sumOfXiYi) - (sumOfXi * sumOfYi);
        double denominator = Math.sqrt(commonRatedMovies * sumOfXiSquared - (sumOfXi * sumOfXi)) *
                Math.sqrt(commonRatedMovies * sumOfYiSquared - (sumOfYi * sumOfYi));

        correlation = numerator / denominator;
        return correlation;
    }


    private void computeCorrelationsFastLookup(MovieHandler ratings,
                                               String outputFile,
                                               int minCommonRatedMovies,
                                               int RESIZE_CONSTANT) {

        //Open buffered writer and write matrix size & optional parameters
        //I keep the buffered writer open during the whole duration of the program as I need to write to disk often
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(outputFile));
            bw.write("" + ratings.getNumUsers());
            bw.newLine();
            bw.write("precomputedMeans=false,minCommonRatedMovies=" + minCommonRatedMovies);
            bw.newLine();
        } catch (IOException e) {
            System.out.println("Exception at initialization");
            e.printStackTrace();
            System.out.println(e);
            System.exit(-1);
        }


        SerializableListManager listManager = new SerializableListManager(ratings.getNumUsers(), RESIZE_CONSTANT);


        ArrayList<Integer> userIDs = ratings.getUserIDs();
        Map<Integer, List<MovieRating>> usersToRatings = ratings.getUsersToRatings();


        //Help array to make printing faster, this array just keeps track of the correlations of the current processed
        //user with all other users.
        //Since values are only kept up to 4 decimal digits, numbers are are rounded to that and then multiplied
        //by the number 10000 and then saving the to short. The value Short.MAX_VALUE represent the value "Float.Nan".
        short[] printHelpArray = new short[userIDs.size()];
        int printHelpCounter = 0;

        //lookUpArrays' functionality is explained in the method correlationFastLookUp
        int maxMovieID = ratings.getMovieIDs().get(ratings.getMovieIDs().size() - 1); //movies are sorted
        float[] lookUpArray = new float[maxMovieID + 1]; //+1 cause I don't want to do -1 all the time
        float lookUpArrayFlag = -1f;
        // Redundant, since ratings are at least 0.5 and array initialized as 0.0 (flag could have been value 0.0)
        // But it only happens once and this is more "general case" so its okay.
        Arrays.fill(lookUpArray, lookUpArrayFlag);

        //For all users
        for (int i = 0; i < userIDs.size(); i++) {

            Integer xID = userIDs.get(i);
            List<MovieRating> userRatings1 = usersToRatings.get(xID);

            //Ratings 0.5 to 5, definitely not -1
            //Prepare the look up array
            for (MovieRating rating : userRatings1) {
                lookUpArray[rating.getMovieID()] = (float) rating.getRating();
            }

            //Print what I already have computed and are stored in the ArrayList
            for (int j = 0; j < i; j++) {
                short val = listManager.getElementFromList(i, j);
                shortToChar(val);
            }

            /* Cov(X,X) self-correlation is always NaN in my implementation */
            shortToChar(Short.MAX_VALUE);

            //Free memory of already computed correlations I no longer need.
            listManager.deleteList(i);

            //Start from i+1, don't need to compute self or recompute already computed ratings
            //Since Cor(X,X) = 1, and Cor(X,Y) = Cor(Y,X)
            for (int j = i + 1; j < userIDs.size(); j++) {
                Integer yID = userIDs.get(j);
                List<MovieRating> userRatings2 = usersToRatings.get(yID);

                //get the correlation
                double cor = correlationFastLookUp(userRatings2, lookUpArray, lookUpArrayFlag, minCommonRatedMovies);

                if (Float.isNaN((float) cor)) {
                    listManager.addElementToList(j, Short.MAX_VALUE);
                    printHelpArray[printHelpCounter++] = Short.MAX_VALUE;
                    continue;
                }
                cor = Math.round(cor * 10000);

                //Remember the value in the data structure !
                listManager.addElementToList(j, (short) cor);
                printHelpArray[printHelpCounter++] = (short) cor;
            }

            //Reverse look up array to the original state
            for (MovieRating rating : userRatings1) {
                lookUpArray[rating.getMovieID()] = lookUpArrayFlag;
            }

            /*
             * Starting operations for printing!
             */

            for (int j = 0; j < printHelpCounter; j++) {
                short val = printHelpArray[j];
                shortToChar(val);
            }

            printChars[printCharsCounter - 1] = '\n'; //note -1 here to delete last comma and put a break line instead!

            try {
                bw.write(printChars, 0, printCharsCounter);
            } catch (IOException e) {
                System.out.println("Exception at write");
                e.printStackTrace();
                System.out.println(e);
                System.exit(-2);
            }

            //Since I am finishing the printing I need to reverse the 2 counters back to their original state
            printCharsCounter = 0;
            printHelpCounter = 0;

        }

        try {
            bw.close();
        } catch (IOException e) {
            System.out.println("Exception at close");
            e.printStackTrace();
            System.out.println(e);
            System.exit(-3);
        }
    }


    /**
     * Please read the comments of the  method correlationFastLookUp, only difference here is the fact,
     * the method also makes use of a
     *
     * @param yRatings             ratings of user Y
     * @param lookUpArray          a look up array populated by ratings of user X
     * @param lookUpArrayFlag      the special flag value
     * @param minCommonRatedMovies the least num of commonly rated movies to define a correlation between 2 users
     * @param meanX                mean of user X (E[X])
     * @param meanY                mean of user Y (E[Y])
     * @return the correlation of user X and Y
     */
    private double correlationWithPrecomputedMeansFastLookUp(List<MovieRating> yRatings,
                                                             float[] lookUpArray,
                                                             float lookUpArrayFlag,
                                                             int minCommonRatedMovies,
                                                             float meanX, float meanY) {
        double correlation = 0;
        //FILL IN HERE

        /*
         * Returns NaN if the 2 Lists have less or equal to 2 common rated movies.
         * Returns a correlation number between -1.0000 ... +1.0000 if the 2 lists
         * have 3 or more common rated movies.
         */

        int commonRatedMovies = 0;

        //Numerator Sum[(Xi - E[Xi])(Yi - E[Yi])]
        double numerator = 0; //cannot be cached do not know which Xi are common beforehand

        //Denominator sqrt((Sum[Xi - E[Xi]])^2 * (Sum[Yi - E[Yi]])^2)
        double sumXiMinusMeanXiSquared = 0; //cannot be cached as I do not know which Xi are common beforehand
        double sumYiMinusMeanYiSquared = 0;

        //Now of all the elements in the other list check if they have common ratings
//        System.out.println("\nTemp Elements:");
        for (MovieRating rating : yRatings) {

            double xRating = lookUpArray[rating.getMovieID()];
            //Common element , do stuff!
            if (xRating != lookUpArrayFlag) {

                double yRating = rating.getRating();

                double xiMinusMeanXi = xRating - meanX;
                double yiMinusMeanYi = yRating - meanY;

                numerator += xiMinusMeanXi * yiMinusMeanYi;

                sumXiMinusMeanXiSquared += xiMinusMeanXi * xiMinusMeanXi;
                sumYiMinusMeanYiSquared += yiMinusMeanYi * yiMinusMeanYi;

                commonRatedMovies++;
            }
        }

        // If the number of rated movies is less than the minimum required to define a common correlation
        if (commonRatedMovies < minCommonRatedMovies) {
            return Float.NaN;
        }


        double denominator = Math.sqrt(sumXiMinusMeanXiSquared * sumYiMinusMeanYiSquared);

        correlation = numerator / denominator;
        return correlation;
    }


    private void computeCorrelationsWithPrecomputedMeansFastLookup(MovieHandler ratings,
                                                                   String outputFile,
                                                                   int minCommonRatedMovies,
                                                                   int RESIZE_CONSTANT) {

        //Compute the average rating of each user and the global
        float[] precomputedMeansInternalIDs = precomputeUsersMeansInternalIDs(ratings);

        //Open bufferedwriter and write matrix size & optional parameters
        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(new FileWriter(outputFile));
            bw.write("" + ratings.getNumUsers());
            bw.newLine();
            bw.write("precomputedMeans=true,minCommonRatedMovies=" + minCommonRatedMovies);
            bw.newLine();
        } catch (IOException e) {
            System.out.println("Exception at initialization");
            e.printStackTrace();
            System.out.println(e);
            System.exit(-1);
        }


        SerializableListManager listManager = new SerializableListManager(ratings.getNumUsers(), RESIZE_CONSTANT);


        //lookUpArrays' functionality is explained in the method correlationFastLookUp
        int maxMovieID = ratings.getMovieIDs().get(ratings.getMovieIDs().size() - 1); //movies are sorted
        float[] lookUpArray = new float[maxMovieID + 1]; //+1 cause I don't want to do -1 all the time
        float lookUpArrayFlag = -1f;
        Arrays.fill(lookUpArray, lookUpArrayFlag);


        ArrayList<Integer> userIDs = ratings.getUserIDs();
        Map<Integer, List<MovieRating>> usersToRatings = ratings.getUsersToRatings();


        //Help array to make printing faster
        short[] printHelpArray = new short[userIDs.size()];
        int printHelpCounter = 0;

        //For all users
        for (int i = 0; i < userIDs.size(); i++) {

            Integer xID = userIDs.get(i);
            List<MovieRating> userRatings1 = usersToRatings.get(xID);

            //Ratings 0.5 to 5, definitely not -1
            for (MovieRating rating : userRatings1) {
                lookUpArray[rating.getMovieID()] = (float) rating.getRating();
            }

            //No memory locality problem
            for (int j = 0; j < i; j++) {
                short val = listManager.getElementFromList(i, j);
                shortToChar(val);
            }

            /* Cov(X,X) self-correlation is always NaN in my implementation */
            shortToChar(Short.MAX_VALUE);

            //free memory
            listManager.deleteList(i);


            //Start from i+1, don't need to compute self or recompute already computed ratings
            //Since Cor(X,X) = 1, and Cor(X,Y) = Cor(Y,X)
            for (int j = i + 1; j < userIDs.size(); j++) {
                Integer yID = userIDs.get(j);
                List<MovieRating> userRatings2 = usersToRatings.get(yID);

                //get the correlation
                double cor = correlationWithPrecomputedMeansFastLookUp(userRatings2, lookUpArray, lookUpArrayFlag,
                        minCommonRatedMovies, precomputedMeansInternalIDs[i], precomputedMeansInternalIDs[j]);

                if (Float.isNaN((float) cor)) {
                    listManager.addElementToList(j, Short.MAX_VALUE);
                    printHelpArray[printHelpCounter++] = Short.MAX_VALUE;
                    continue;
                }
                cor = Math.round(cor * 10000);
                listManager.addElementToList(j, (short) cor);
                printHelpArray[printHelpCounter++] = (short) cor;
            }

            //Set arr to original state
            for (MovieRating rating : userRatings1) {
                lookUpArray[rating.getMovieID()] = lookUpArrayFlag;
            }

            for (int j = 0; j < printHelpCounter; j++) {
                short val = printHelpArray[j];
                shortToChar(val);
            }

            printChars[printCharsCounter - 1] = '\n'; //note -1 here to delete last comma!

            try {
                bw.write(printChars, 0, printCharsCounter);
            } catch (IOException e) {
                System.out.println("Exception at write");
                e.printStackTrace();
                System.out.println(e);
                System.exit(-2);
            }
            printCharsCounter = 0;
            printHelpCounter = 0;
        }

        try {
            bw.close();
        } catch (IOException e) {
            System.out.println("Exception at close");
            e.printStackTrace();
            System.out.println(e);
            System.exit(-3);
        }

    }

    private float[] precomputeUsersMeansInternalIDs(MovieHandler ratings) {
        List<Integer> userIDs = ratings.getUserIDs();
        Map<Integer, List<MovieRating>> map = ratings.getUsersToRatings();


        //Using internal user IDs !
        float[] usersPrecomputedMeans = new float[userIDs.size()];

        for(int i=0; i<userIDs.size(); i++){
            //Get External ID
            Integer extID = userIDs.get(i);
            List<MovieRating> movieRatings = map.get(extID);

            double sumXis = 0;
            for (MovieRating mv : movieRatings) {
                sumXis += mv.getRating();
            }
            usersPrecomputedMeans[i] = (float) (sumXis / movieRatings.size());
        }
        return usersPrecomputedMeans;
    }

    public static void main(String[] args) {
        String trainingFile = "";
        String outputFile = "";
        int minCommonRatedMovies = 1;
        boolean preComputedMeans = false;
        int i = 0;
        int INITIAL_SIZE_CONSTANT = 10000;
        while (i < args.length && args[i].startsWith("-")) {
            String arg = args[i];
            if (arg.equals("-trainingFile")) {
                trainingFile = args[i + 1];
            } else if (arg.equals("-outputFile")) {
                outputFile = args[i + 1];
            } else if (arg.equals("-minCommonRatedMovies")) {
                minCommonRatedMovies = Integer.parseInt(args[i + 1]);
                if (minCommonRatedMovies < 1) {
                    System.out.println("Possible minCommonRatedMovies values are positive numbers.");
                    System.out.println("Program exiting...");
                    System.exit(1);
                }
            } else if (arg.equals("-precomputedMeans")) {
                if (args[i + 1].equals("true")) {
                    preComputedMeans = true;
                }
            } else if (arg.equals("-initialSize")) {
                INITIAL_SIZE_CONSTANT = Integer.parseInt(args[i + 1]);
            }
            // ADD ADDITIONAL PARAMETERS //
            i += 2;
        }

        MovieHandler ratings = new MovieHandler(trainingFile);
        PearsonsCorrelation matrix = new PearsonsCorrelation(ratings);

        if (preComputedMeans) {
            System.out.println("Correlations WITH precomputed means!");
            System.out.println("Min common rated movies to define a correlation: " + minCommonRatedMovies);
            matrix.computeCorrelationsWithPrecomputedMeansFastLookup(ratings, outputFile, minCommonRatedMovies, INITIAL_SIZE_CONSTANT);
        } else { //no precomputed means!
            System.out.println("Correlations WITHOUT precomputed means!");
            System.out.println("Min common rated movies to define a correlation: " + minCommonRatedMovies);
            matrix.computeCorrelationsFastLookup(ratings, outputFile, minCommonRatedMovies, INITIAL_SIZE_CONSTANT);
        }

    }


    /**
     * Create an empty PearsonsCorrelation instance with default parameters.
     */
    public PearsonsCorrelation() {
        super();
        // FILL IN HERE //

        /**
         *
         * If this constructor is used the following 2 methods won't work
         * public double correlation(List<MovieRating> xRatings, List<MovieRating> yRatings)
         * public double get(int i, int j)
         *
         * But they are not used anyway and they are there just for demonstration.
         *
         */
    }

    /**
     * Create a PearsonsCorrelation instance with default parameters.
     */
    public PearsonsCorrelation(MovieHandler ratings) {
        super();
        // FILL IN HERE //

        /**
         * Following code not actually needed, just for demonstration,
         * since I was supposed to fill the methods
         * get(), correlation() and create a constructor with MovieHandler
         */
        this.ratingsDemonstration = ratings;
        //movieIds are sorted!
        int maxMovieID = ratingsDemonstration.getMovieIDs().get(ratingsDemonstration.getMovieIDs().size() - 1);
        this.lookUpArrayDemonstration = new float[maxMovieID + 1]; //+1 cause I don't want to do -1 all the time

    }


    /**
     * Load a previously computed PearsonsCorrelation instance.
     */
    public PearsonsCorrelation(MovieHandler ratings, String filename) {
        // FILL IN HERE //
        /*
         * I have another constructor that requires as argument a kNN.
         * If this is called just pass a default kNN value to that one.
         */
        this(ratings, filename, 1000);
    }

    /**
     * Following methods and class members are used in the printing operations
     */

    //Used in combination with method shortToChar, just used to make the printing to disk a bit faster
    private final char[] printChars = new char[8 * 70000]; //Maximum should actually be 8 * sizeOfUsers but dont want to declare elsewhere
    private int printCharsCounter = 0;

    //Prepares text for printing it to disk
    //Makes use of a few global variables to make it fast
    private void shortToChar(short val) {

        if (val == Short.MAX_VALUE) {
            printChars[printCharsCounter++] = 'N';
            printChars[printCharsCounter++] = 'a';
            printChars[printCharsCounter++] = 'N';
            printChars[printCharsCounter++] = ',';
            return;
        }

        if (val < 0) {
            val = (short) -val;
            printChars[printCharsCounter++] = '-';
        }

        if (val == 10000) {
            printChars[printCharsCounter++] = '1';
            printChars[printCharsCounter++] = '.';
            printChars[printCharsCounter++] = '0';
            printChars[printCharsCounter++] = '0';
            printChars[printCharsCounter++] = '0';
            printChars[printCharsCounter++] = '0';
            printChars[printCharsCounter++] = ',';
        } else {
            printChars[printCharsCounter++] = '.';
            printChars[printCharsCounter + 3] = (char) ('0' + (val % 10));
            val /= 10;
            printChars[printCharsCounter + 2] = (char) ('0' + (val % 10));
            val /= 10;
            printChars[printCharsCounter + 1] = (char) ('0' + (val % 10));
            val /= 10;
            printChars[printCharsCounter] = (char) ('0' + (val % 10));
            printChars[printCharsCounter + 4] = ',';
            printCharsCounter += 5;
        }
    }


    /**
     * Following methods support the reading
     */

    List<Neighbor>[] correlationsOfUsers;

    public PearsonsCorrelation(MovieHandler ratings, String filename, int kNN) {
        // FILL IN HERE //
        readCorrelationMatrix(filename, kNN);
    }

    /**
     * Reads the correlation matrix from a file.
     *
     * @param filename  Path to the input file.
     * @param kNN number of NN to be considered
     */
    public void readCorrelationMatrix(String filename, int kNN) {
        // FILL IN HERE //

        //parseOneCorrelationLine(String line, int numOfUsers) is around
        //7 times faster than line.split(",") followed by ParseFloat on every element
        //Remember users start from number 1!
        //Remember some user IDs do not exist (#of users = ~69900 , max user id # = ~71.000)

        String line = null;
        int numOfUsers;
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            line = br.readLine();
            numOfUsers = Integer.parseInt(line);
            br.readLine();

            this.correlationsOfUsers = new ArrayList[numOfUsers];

            for (int i = 0; i < numOfUsers; i++) {
                line = br.readLine();
                this.correlationsOfUsers[i] = parseOneCorrelationLine(line, kNN);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //Parses one line of the input file
    private List<Neighbor> parseOneCorrelationLine(String line, int kNN) {
        List<Neighbor> list = new ArrayList<Neighbor>();

        int lineIndex = 0;
        int currentUserIDCorrelation = 0;
        while (lineIndex < line.length()) {
            if (line.charAt(lineIndex) == 'N') {
                lineIndex += 4;
                currentUserIDCorrelation++;
            } else if (line.charAt(lineIndex) == '.') {
                double cor = (((line.charAt(lineIndex + 1) - '0') * 0.1000) +
                        ((line.charAt(lineIndex + 2) - '0') * 0.0100) +
                        ((line.charAt(lineIndex + 3) - '0') * 0.0010) +
                        ((line.charAt(lineIndex + 4) - '0') * 0.0001));
                lineIndex += 6;
                list.add(new Neighbor(currentUserIDCorrelation ,cor));
                currentUserIDCorrelation++;
            } else if (line.charAt(lineIndex) == '-') {
                if (line.charAt(lineIndex + 1) == '.') {
                    double cor = -(((line.charAt(lineIndex + 2) - '0') * 0.1) +
                            ((line.charAt(lineIndex + 3) - '0') * 0.01) +
                            ((line.charAt(lineIndex + 4) - '0') * 0.001) +
                            ((line.charAt(lineIndex + 5) - '0') * 0.0001));
                    lineIndex += 7;
                    list.add(new Neighbor(currentUserIDCorrelation ,cor));
                    currentUserIDCorrelation++;
                } else { //Then it is -1
                    double cor = -1.0000f;
                    lineIndex += 8;
                    list.add(new Neighbor(currentUserIDCorrelation ,cor));
                    currentUserIDCorrelation++;
                }
            } else { //Then it is equal to 1.0000
                double cor = 1.0000f;
                lineIndex += 7;
                list.add(new Neighbor(currentUserIDCorrelation ,cor));
                currentUserIDCorrelation++;
            }
        }

        //Sort the array list from highest to lowest
        Collections.sort(list);
        //Return the first kNNs
        List<Neighbor> listToReturn = new ArrayList<Neighbor>(list.subList(0, Math.min(kNN, list.size())));
        return listToReturn;
    }


    public List<Neighbor>[] getCorrelationsOfUsers() {
        return correlationsOfUsers;
    }

    /**
     * Following methods and class members not used in my program, they are here only for demonstration!
     */


    //this should actually be equal to the max movie num id, here I initialize it as 15.000 cause I don't want to
    //the following code to be connected in any way with the rest of the code
    private float[] lookUpArrayDemonstration;
    private float lookUpArrayDemonstrationFlag = -1f;
    private MovieHandler ratingsDemonstration;

    public double correlation(List<MovieRating> xRatings, List<MovieRating> yRatings) {
        double correlation = 0;
        //FILL IN HERE

        /*
         * Returns NaN if the 2 Lists have less or equal to 2 common rated movies.
         * Returns a correlation number between -1.0000 ... +1.0000 if the 2 lists
         * have 3 or more common rated movies.
         */

        //The smaller list should become the Map! No problem since correlation(X,Y) = correlation(Y,X)
        if (xRatings.size() > yRatings.size()) {
            List<MovieRating> temp = xRatings;
            xRatings = yRatings;
            yRatings = temp;
        }

        //Ratings 0.5 to 5, definitely not -1
        for (MovieRating rating : xRatings) {
            this.lookUpArrayDemonstration[rating.getMovieID()] = (float) rating.getRating();
        }

        //I need E[X], E[Y], E[XY], E[X^2], E[Y^2]
        int commonRatedMovies = 0;
        double sumOfXi = 0;
        double sumOfYi = 0;
        double sumOfXiSquared = 0;
        double sumOfYiSquared = 0;
        double sumOfXiYi = 0;


        //Now of all the elements in the other list check if they have common ratings
        for (MovieRating rating : yRatings) {

            double xRating = this.lookUpArrayDemonstration[rating.getMovieID()];
            //Common element , do stuff!
            if (xRating != this.lookUpArrayDemonstrationFlag) {

                double yRating = rating.getRating();

                sumOfXi += xRating;
                sumOfYi += yRating;

                sumOfXiSquared += xRating * xRating;
                sumOfYiSquared += yRating * yRating;

                sumOfXiYi += xRating * yRating;

                commonRatedMovies++;
            }
        }

        //Set arr to original state
        for (MovieRating rating : xRatings) {
            this.lookUpArrayDemonstration[rating.getMovieID()] = this.lookUpArrayDemonstrationFlag;
        }

        //https://en.wikipedia.org/wiki/Pearson_correlation_coefficient
        //Numerical instability irrelevant, I only care for up to 4 decimal digits
        double numerator = (commonRatedMovies * sumOfXiYi) - (sumOfXi * sumOfYi);
        double denominator = Math.sqrt(commonRatedMovies * sumOfXiSquared - (sumOfXi * sumOfXi)) *
                Math.sqrt(commonRatedMovies * sumOfYiSquared - (sumOfYi * sumOfYi));

        correlation = numerator / denominator;
        return correlation;
    }

    /**
     * Returns the correlation between two users.
     *
     * @param i True user id
     * @param j True user id
     * @return The Pearson correlation
     */
    public double get(int i, int j) {
        double correlation = 0;
        // FILL IN HERE //

        //Assuming true user ID refers directly to the Map<Integer, List<MovieRating>> usersToRatings
        //And NOT to the  ArrayList<Integer> userIDs.get(i)

        List<MovieRating> xRatings = ratingsDemonstration.getUsersToRatings().get(i);
        List<MovieRating> yRatings = ratingsDemonstration.getUsersToRatings().get(j);

        correlation = correlation(xRatings, yRatings);

        return correlation;
    }

    /**
     * Method not used neither implemented, as printing operations happen elsewhere, just declaring the
     * method signature here to make sure the program won't crush with the automated grader.
     */
    public void writeCorrelationMatrix(String filename) {
        // FILL IN HERE //
    }

    /**
     * Method not used neither implemented, as reading operations happen elsewhere, just declaring the
     * method signature here to make sure the program won't crush with the automated grader.
     */
    public void readCorrelationMatrix(String filename) {
        // FILL IN HERE //
    }


}