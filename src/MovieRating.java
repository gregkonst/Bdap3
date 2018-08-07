import java.util.Objects;

/**
 * Simple class to store movies together with their rating.
 *
 * @author Toon Van Craenendonck
 *
 */
public class MovieRating implements Comparable<MovieRating>{

    private int movieID;
    private double rating;

    public MovieRating(int movieID, double rating) {
        this.movieID = movieID;
        this.rating = rating;
    }

    public int getMovieID() {
        return movieID;
    }

    public double getRating() {
        return rating;
    }

    @Override
    public int compareTo(MovieRating r) {
        if (rating < r.getRating()) {
            return -1;
        } else if (rating == r.getRating()) {

            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public String toString() {
        return "[ID: "+getMovieID()+", Rating: " + getRating() + "]";
    }

}
