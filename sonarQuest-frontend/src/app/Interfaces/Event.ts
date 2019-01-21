import { User } from './User';
export interface Event {
    id: number,
    type: string,
    title: string,
    story: string,
    status: string,
    image: string,
    world_id: number,
    timestamp: Date,
    user: User,
    headline: string
}