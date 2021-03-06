import moment from "moment";

/**
 * Get how much time has passed since a certain date.
 * @param {Number} now current
 * @param {Number} date event occurred
 * @return {string} formatted time since.
 */
export function formatTimeForNow({ now, date }) {
  const dNow = new Date(now);
  const dDate = new Date(date);
  if (moment.isDate(dNow) && moment.isDate(dDate)) {
    return moment(dDate).from(moment(dNow));
  }
}

/**
 * Generate the a human readable form from milliseconds.
 * @param {Number} date to format
 * @return {string} humanized version of the date
 */
export function getHumanizedDuration({ date }) {
  if (date !== null) {
    return moment.duration(date).humanize();
  }
  return "";
}

/**
 * Generate the time from now.  Renders as human readable.s
 * @param {string} date raw string date from server
 * @return {*}
 */
export function fromNow({ date }) {
  const start = new Date(date);
  if (moment.isDate(start)) {
    const t = moment(start).fromNow();
    return t;
  }
  return "";
}

/**
 * Format unix timestamp as human readable string.
 * @param  {Number} date unix timestamp
 * @return {string} formatted date
 */
export function formatDate({ date }) {
  const t = new Date(date);
  if (moment.isDate(t)) {
    return moment(t).format("lll");
  }
  return "";
}
